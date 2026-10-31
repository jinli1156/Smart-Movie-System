package com.movie.ticket.service.listing;

import cn.hutool.core.text.csv.CsvUtil;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.movie.ticket.dto.*;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.security.UserContext;
import com.movie.ticket.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class ListingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ListingAiClient ai;
    private final ListingSourceReader reader;
    private final FileUploadService uploads;
    private final MovieService movies;
    private final CinemaService cinemas;
    private final HallService halls;

    public record Draft(String id, String kind, String batchId, Long cinemaId, long ownerId, String status, JsonNode fields,
        JsonNode extracted, String sourceText, List<String> sourceNames, List<String> sourceImages, String extractionMode, String extractionWarning,
        String reviewNote, Long reviewerId, Long entityId, int version, LocalDateTime createdAt, LocalDateTime updatedAt,
        LocalDateTime reviewedAt, List<String> issues, List<Map<String,Object>> history) {}
    public record Edit(int version, JsonNode fields) {}
    public record Review(int version, boolean acknowledged, String note) {}

    public Map<String,Object> capabilities() {
        requireManager();
        Map<String,Object> schemas=new LinkedHashMap<>();
        for (String kind : allowedKinds()) schemas.put(kind,ListingSchema.fields(kind));
        return Map.of("textConfigured",ai.textConfigured(),"visionConfigured",ai.visionConfigured(),"schemas",schemas);
    }
    private List<String> allowedKinds() { return UserContext.isAdmin()?List.of("MOVIE","CINEMA","HALL"):List.of("HALL"); }
    private void requireManager() {
        if (!UserContext.isManager()) throw new BusinessException(403,"需要管理员权限");
        UserContext.getUserId();
    }
    public Long scope(String kind, Long cinemaId) {
        requireManager(); ListingSchema.fields(kind);
        if (!kind.equals("HALL")) { UserContext.requirePlatformAdmin(); return null; }
        Long id=UserContext.scopeCinemaId(cinemaId);
        UserContext.requireCinema(id);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM cinema WHERE id=? AND deleted=0",Integer.class,id)==0) throw new BusinessException(400,"所属影院不存在");
        return id;
    }
    /** Extraction never writes a live movie, cinema, or hall. */
    public Draft create(String kind, Long cinemaId, String text, List<MultipartFile> files) {
        cinemaId=scope(kind,cinemaId);
        var source=reader.read(text,files,kind);
        ObjectNode fields=structured(kind,source.text());
        String mode="STRUCTURED", warning="";
        if (fields==null) {
            fields=json.createObjectNode(); mode="MANUAL";
            boolean hasImages=!source.images().isEmpty();
            if (hasImages && !ai.visionConfigured() && ai.textConfigured() && !source.text().isBlank()) {
                hasImages=false;
                warning="视觉模型未配置，仅提取文字资料，图片已保留供人工核对。";
            }
            if (hasImages && !ai.visionConfigured()) warning="海报识别未配置，图片已保留；请根据原资料补充字段，或配置视觉模型后重新识别。";
            else if (!hasImages && !ai.textConfigured()) warning="AI 文字提取未配置，原资料已保留；可手动填写，或上传 JSON/CSV 模板。";
            else try {
                JsonNode result=ai.extract(kind,hasImages?source:new ListingSourceReader.Source(source.text(),source.names(),List.of(),source.imageFiles()));
                if (result.path("records").isArray()) {
                    if (result.path("records").size()!=1) throw new BusinessException(400,"该接口仅支持一条资料，多条资料请使用批量导入入口");
                    result=result.path("records").get(0);
                }
                if (result.path("error").asText().equals("MULTIPLE_RECORDS")) throw new BusinessException(400,"一次仅支持上架一部电影、一家影院或一个影厅，请拆分资料");
                if (!result.path("fields").isObject()) throw new IllegalArgumentException("Invalid fields");
                fields=ListingSchema.clean(kind,result.path("fields"),json); mode=hasImages?"AI_VISION":"AI_TEXT";
            } catch (BusinessException e) { throw e; }
            catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Listing extraction failed kind={} errorType={}",kind,e.getClass().getSimpleName());
                warning="AI 提取暂时失败，原资料已保留，请补充字段或稍后新建草稿重试。";
            }
        }
        // Models and input files cannot choose an activation state or set a rating.
        fields.put("status",kind.equals("MOVIE")?2:1);
        List<String> urls=new ArrayList<>();
        if (!source.imageFiles().isEmpty()) try {
            for (MultipartFile image : source.imageFiles()) urls.add(uploads.uploadImage(image).get("url"));
            if (kind.equals("MOVIE")) fields.put("poster",urls.getFirst());
            else if (kind.equals("CINEMA")) fields.put("images",json.writeValueAsString(urls));
        } catch (Exception e) { throw new BusinessException(400,"图片保存失败，请重新上传"); }
        return insertDraft(kind,cinemaId,null,fields,source,urls,mode,warning);
    }
    private Draft insertDraft(String kind,Long cinemaId,String batchId,ObjectNode fields,ListingSourceReader.Source source,List<String> urls,String mode,String warning) {
        String id=UUID.randomUUID().toString(); LocalDateTime now=LocalDateTime.now();
        jdbc.update("INSERT INTO listing_draft(id,kind,batch_id,cinema_id,owner_id,status,fields,extracted,source_text,source_names,source_images,extraction_mode,extraction_warning,review_note,version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'',1,?,?)",
            id,kind,batchId,cinemaId,UserContext.getUserId(),"DRAFT",fields.toString(),fields.toString(),source.text(),write(source.names()),write(urls),mode,warning,now,now);
        logAction(id,"CREATED","",1); return get(id);
    }
    /** Called after file parsing and model requests finish, so only database writes hold a transaction. */
    @Transactional public List<Draft> createImported(String kind,Long cinemaId,String batchId,List<ObjectNode> records,
        ListingSourceReader.Source source,List<String> urls,String mode,String warning) {
        cinemaId=scope(kind,cinemaId); ListingRecordParser.limit(records.size());
        List<Draft> drafts=new ArrayList<>();
        for (ObjectNode record:records) {
            ObjectNode fields=ListingSchema.clean(kind,record,json);
            fields.put("status",kind.equals("MOVIE")?2:1);
            if (records.size()==1 && !urls.isEmpty()) {
                if (kind.equals("MOVIE")) fields.put("poster",urls.getFirst());
                if (kind.equals("CINEMA")) fields.put("images",write(urls));
            }
            drafts.add(insertDraft(kind,cinemaId,batchId,fields,source,urls,mode,warning));
        }
        return drafts;
    }
    public List<Map<String,Object>> batch(String batchId) {
        requireManager(); List<Object> args=new ArrayList<>(List.of(batchId)); String where=" WHERE batch_id=?";
        if (UserContext.isCinemaAdmin()) { where+=" AND kind='HALL' AND cinema_id=?"; args.add(UserContext.scopeCinemaId(null)); }
        List<Map<String,Object>> records=jdbc.query("SELECT id,kind,status,fields,extraction_mode,updated_at FROM listing_draft"+where+" ORDER BY created_at,id",(rs,n)-> {
            JsonNode fields=read(rs.getString("fields")); String title=fields.path("title").asText(fields.path("name").asText(""));
            return Map.<String,Object>of("id",rs.getString("id"),"kind",rs.getString("kind"),"status",rs.getString("status"),"title",title.isBlank()?"未命名资料":title,
                "extractionMode",rs.getString("extraction_mode"),"updatedAt",rs.getObject("updated_at",LocalDateTime.class));
        },args.toArray());
        if (records.isEmpty()) throw new BusinessException(404,"导入批次不存在或无权查看");
        return records;
    }
    private ObjectNode structured(String kind,String text) {
        String clean=text.strip();
        if (clean.startsWith("[")) throw new BusinessException(400,"一次只支持一条资料，请使用单个 JSON 对象或单行 CSV");
        JsonNode input=null;
        try {
            if (clean.startsWith("{")) {
                input=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(clean);
                if (input.has("fields")) input=input.get("fields");
            } else {
                var rows=CsvUtil.getReader().readFromStr(clean).getRows();
                if (rows.size()>=2 && rows.getFirst().size()>1) {
                    ObjectNode row=json.createObjectNode(); var header=rows.getFirst();
                    Set<String> allowed=new HashSet<>(); ListingSchema.fields(kind).forEach(f->allowed.add(f.key()));
                    if (allowed.contains(header.get(0))) {
                        if (rows.size()!=2 || rows.get(1).size()!=header.size()) throw new BusinessException(400,"CSV 需为英文表头和一行完整资料；多条资料请拆分");
                        for (int i=0;i<header.size();i++) row.put(header.get(i),rows.get(1).get(i));
                        input=row;
                    }
                }
            }
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { if (clean.startsWith("{")) throw new BusinessException(400,"JSON 格式无效，请检查文件"); }
        if (input==null) return null;
        ObjectNode result=ListingSchema.clean(kind,input,json);
        if (result.isEmpty()) throw new BusinessException(400,"资料中没有匹配字段，请下载对应类型模板");
        return result;
    }
    public Map<String,Object> list(String kind,String status,int page,int size) {
        requireManager();
        if (page<1 || page>10000 || size<1 || size>50) throw new BusinessException(400,"分页参数无效");
        List<Object> args=new ArrayList<>(); String where=" WHERE 1=1";
        if (UserContext.isCinemaAdmin()) { where+=" AND kind='HALL' AND cinema_id=?"; args.add(UserContext.scopeCinemaId(null)); }
        if (kind!=null && !kind.isBlank()) {
            if (!allowedKinds().contains(kind)) throw new BusinessException(403,"无权查看该类型资料");
            where+=" AND kind=?"; args.add(kind);
        }
        if (status!=null && !status.isBlank()) {
            if (!Set.of("DRAFT","REJECTED","PUBLISHED").contains(status)) throw new BusinessException(400,"草稿状态无效");
            where+=" AND status=?"; args.add(status);
        }
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM listing_draft"+where,Long.class,args.toArray());
        List<Object> paged=new ArrayList<>(args); paged.add(size); paged.add((page-1)*size);
        var rows=jdbc.query("SELECT id,kind,status,fields,extraction_mode,updated_at FROM listing_draft"+where+" ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",(rs,n)-> {
            JsonNode fields=read(rs.getString("fields"));
            String title=fields.path("title").asText(fields.path("name").asText(""));
            return Map.of("id",rs.getString("id"),"kind",rs.getString("kind"),"status",rs.getString("status"),
                "title",title.isBlank()?"未命名资料":title,"extractionMode",rs.getString("extraction_mode"),"updatedAt",rs.getObject("updated_at",LocalDateTime.class));
        },paged.toArray());
        return Map.of("records",rows,"total",count,"page",page,"size",size);
    }
    public Draft get(String id) { return load(id,false); }
    private Draft load(String id,boolean lock) {
        requireManager();
        var rows=jdbc.query("SELECT * FROM listing_draft WHERE id=?"+(lock?" FOR UPDATE":""),(rs,n)->map(rs),id);
        if (rows.isEmpty()) throw new BusinessException(404,"上架草稿不存在");
        Draft draft=rows.getFirst();
        if (UserContext.isCinemaAdmin() && (!draft.kind().equals("HALL") || !Objects.equals(draft.cinemaId(),UserContext.scopeCinemaId(null)))) throw new BusinessException(403,"无权查看其他影院或平台资料");
        return draft;
    }
    private Draft map(ResultSet rs) throws SQLException {
        JsonNode fields=read(rs.getString("fields")); String kind=rs.getString("kind");
        List<String> issues=new ArrayList<>(ListingSchema.issues(kind,fields,json));
        if (duplicate(kind,(Long)rs.getObject("cinema_id"),fields)) issues.add("系统中已有同名资料，请核对，避免重复上架");
        var history=jdbc.queryForList("SELECT operator_id AS operatorId,action,note,version,created_at AS createdAt FROM listing_review_log WHERE draft_id=? ORDER BY created_at,version",rs.getString("id"));
        List<String> names=new ArrayList<>(),images=new ArrayList<>(); read(rs.getString("source_names")).forEach(n->names.add(n.asText())); read(rs.getString("source_images")).forEach(n->images.add(n.asText()));
        return new Draft(rs.getString("id"),kind,rs.getString("batch_id"),(Long)rs.getObject("cinema_id"),rs.getLong("owner_id"),rs.getString("status"),fields,
            read(rs.getString("extracted")),rs.getString("source_text"),names,images,rs.getString("extraction_mode"),rs.getString("extraction_warning"),rs.getString("review_note"),
            (Long)rs.getObject("reviewer_id"),(Long)rs.getObject("entity_id"),rs.getInt("version"),rs.getObject("created_at",LocalDateTime.class),rs.getObject("updated_at",LocalDateTime.class),rs.getObject("reviewed_at",LocalDateTime.class),issues,history);
    }
    @Transactional public Draft save(String id,Edit edit) {
        Draft d=load(id,true); editable(d,edit.version());
        ObjectNode fields=ListingSchema.clean(d.kind(),edit.fields(),json);
        jdbc.update("UPDATE listing_draft SET fields=?,status='DRAFT',version=version+1,updated_at=? WHERE id=?",fields.toString(),LocalDateTime.now(),id);
        logAction(id,"SAVED","",d.version()+1); return get(id);
    }
    @Transactional public Draft reject(String id,Review review) {
        Draft d=load(id,true); editable(d,review.version());
        String note=Objects.toString(review.note(),"").strip();
        if (note.isEmpty() || note.length()>500) throw new BusinessException(400,"请填写 1-500 字驳回原因");
        jdbc.update("UPDATE listing_draft SET status='REJECTED',review_note=?,reviewer_id=?,reviewed_at=?,version=version+1,updated_at=? WHERE id=?",note,UserContext.getUserId(),LocalDateTime.now(),LocalDateTime.now(),id);
        logAction(id,"REJECTED",note,d.version()+1); return get(id);
    }
    /** A row lock, version check, and unique publication key protect retries and concurrent reviewers. */
    @Transactional public Draft publish(String id,Review review) {
        Draft d=load(id,true);
        if (d.status().equals("PUBLISHED")) return d;
        editable(d,review.version());
        if (!d.status().equals("DRAFT")) throw new BusinessException(409,"驳回资料请修改并暂存后重新审核");
        if (!review.acknowledged()) throw new BusinessException(400,"请先确认已核对原资料和所有字段");
        scope(d.kind(),d.cinemaId());
        List<String> issues=ListingSchema.issues(d.kind(),d.fields(),json);
        if (!issues.isEmpty()) throw new BusinessException(400,String.join("；",issues));
        if (duplicate(d.kind(),d.cinemaId(),d.fields())) throw new BusinessException(409,"系统中已有同名资料，请勿重复上架");
        String identity=identity(d);
        String table=switch(d.kind()) { case "MOVIE" -> "movie"; case "CINEMA" -> "cinema"; case "HALL" -> "hall"; default -> throw new IllegalStateException(); };
        jdbc.update("UPDATE listing_draft SET publication_key=NULL WHERE publication_key=? AND status='PUBLISHED' AND entity_id IN (SELECT id FROM "+table+" WHERE deleted=1)",identity);
        jdbc.update("UPDATE listing_draft SET publication_key=? WHERE id=?",identity,id);
        Long entityId;
        try {
            entityId=switch(d.kind()) {
                case "MOVIE" -> { var dto=json.treeToValue(d.fields(),MovieDTO.class); dto.setRating(BigDecimal.ZERO); yield movies.create(dto); }
                case "CINEMA" -> cinemas.create(json.treeToValue(d.fields(),CinemaDTO.class));
                case "HALL" -> { var dto=json.treeToValue(d.fields(),HallDTO.class); dto.setCinemaId(d.cinemaId()); yield halls.create(dto); }
                default -> throw new IllegalStateException();
            };
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException(400,"字段类型无效，请检查后重新暂存"); }
        jdbc.update("UPDATE listing_draft SET status='PUBLISHED',entity_id=?,reviewer_id=?,reviewed_at=?,review_note='',version=version+1,updated_at=? WHERE id=?",entityId,UserContext.getUserId(),LocalDateTime.now(),LocalDateTime.now(),id);
        logAction(id,"PUBLISHED","",d.version()+1); return get(id);
    }
    private void editable(Draft d,int version) {
        if (d.status().equals("PUBLISHED")) throw new BusinessException(409,"已发布资料不能修改，请在对应管理页面编辑");
        if (d.version()!=version) throw new BusinessException(409,"资料已被其他管理员修改，请重新打开后再操作");
    }
    private boolean duplicate(String kind,Long cinemaId,JsonNode fields) {
        String name=fields.path(kind.equals("MOVIE")?"title":"name").asText("").strip();
        if (name.isBlank()) return false;
        return switch(kind) {
            case "MOVIE" -> jdbc.queryForObject("SELECT COUNT(*) FROM movie WHERE deleted=0 AND title=?",Integer.class,name)>0;
            case "CINEMA" -> jdbc.queryForObject("SELECT COUNT(*) FROM cinema WHERE deleted=0 AND name=? AND address=?",Integer.class,name,fields.path("address").asText(""))>0;
            case "HALL" -> jdbc.queryForObject("SELECT COUNT(*) FROM hall WHERE deleted=0 AND cinema_id=? AND name=?",Integer.class,cinemaId,name)>0;
            default -> false;
        };
    }
    private String identity(Draft d) {
        String key=d.kind()+":"+d.fields().path(d.kind().equals("MOVIE")?"title":"name").asText().strip().toLowerCase(Locale.ROOT);
        if (d.kind().equals("HALL")) key+=":"+d.cinemaId();
        if (d.kind().equals("CINEMA")) key+=":"+d.fields().path("address").asText().strip().toLowerCase(Locale.ROOT);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private void logAction(String id,String action,String note,int version) {
        jdbc.update("INSERT INTO listing_review_log(id,draft_id,operator_id,action,note,version,created_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,UserContext.getUserId(),action,note,version,LocalDateTime.now());
    }
    private JsonNode read(String value) { try { return json.readTree(value); } catch(Exception e) { throw new IllegalStateException("Invalid stored listing data",e); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
}
