package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.security.UserContext;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.movie.ticket.service.marketing.MarketingStore.*;

@Service
public class MarketingService {
    final MarketingStore db;
    final MarketingSettings settings;
    final MarketingTextClient text;
    final MarketingAssets assets;
    public MarketingService(MarketingStore db,MarketingSettings settings,MarketingTextClient text,MarketingAssets assets) {
        this.db=db; this.settings=settings; this.text=text; this.assets=assets;
    }
    private void manager() { if(!UserContext.isManager()) throw new BusinessException(403,"需要影院管理权限"); }
    Map<String,Object> campaign(String id,boolean lock) {
        manager();
        var c=db.one("SELECT * FROM marketing_campaign WHERE id=?"+(lock?" FOR UPDATE":""),id);
        UserContext.requireCinema(num(c,"cinema_id"));
        return c;
    }
    public Map<String,Object> capabilities() {
        manager();
        return Map.of("imageConfigured",settings.imageConfigured(),"textConfigured",text.configured(),
            "posterContentRequired",true,
            "imageModel",settings.getImageModel(),"imageProvider",settings.getImageProvider(),"maxImagesPerBatch",settings.getMaxImagesPerBatch(),
            "dailyImageLimit",settings.getDailyImageLimit(),"styles",List.of("自由创作"),
            "ratios",List.of("16:9","3:4","1:1"));
    }
    public List<Map<String,Object>> campaigns() {
        manager(); Long cinema=UserContext.scopeCinemaId(null);
        return db.rows("SELECT * FROM marketing_campaign"+(cinema==null?"":" WHERE cinema_id=?")+" ORDER BY created_at DESC LIMIT 100",
            cinema==null?new Object[]{}:new Object[]{cinema}).stream().map(db::output).toList();
    }
    public Map<String,Object> createCampaign(MarketingDtos.Campaign input) {
        manager(); UserContext.requireCinema(input.cinemaId());
        if(!Set.of("自由创作","简洁","节日").containsAll(input.styles())||!Set.of("16:9","3:4","1:1").containsAll(input.ratios()))
            throw new BusinessException(400,"请选择支持的风格和图片比例");
        cinemaFacts(input.cinemaId());
        var movies=new ArrayList<Map<String,Object>>();
        for(Long movieId:new LinkedHashSet<>(input.movieIds())) movies.add(movieFacts(movieId));
        String cid=id();
        db.update("INSERT INTO marketing_campaign(id,cinema_id,owner_id,name,brief,audience,styles,ratios,movie_snapshot,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
            cid,input.cinemaId(),UserContext.getUserId(),input.name().trim(),input.brief().trim(),input.audience().trim(),
            db.stringify(new LinkedHashSet<>(input.styles())),db.stringify(new LinkedHashSet<>(input.ratios())),db.stringify(movies),LocalDateTime.now());
        return detail(cid);
    }
    public Map<String,Object> detail(String campaignId) {
        var c=campaign(campaignId,false); var result=db.output(c);
        result.put("tasks",db.rows("SELECT * FROM aigc_task WHERE campaign_id=? ORDER BY created_at,id",campaignId).stream().map(db::output).toList());
        result.put("assets",db.rows("SELECT * FROM marketing_asset WHERE campaign_id=? ORDER BY created_at DESC",campaignId).stream().map(a->{
            var out=db.output(a); out.put("previewUrl","/api/admin/marketing/assets/"+str(a,"id")+"/file"); return out;
        }).toList());
        result.put("versions",db.rows("SELECT * FROM marketing_content_version WHERE campaign_id=? ORDER BY version DESC",campaignId).stream().map(db::output).toList());
        result.put("publications",db.rows("SELECT * FROM marketing_publication WHERE campaign_id=? ORDER BY created_at DESC",campaignId).stream().map(db::output).toList());
        result.put("logs",db.rows("SELECT * FROM marketing_review_log WHERE campaign_id=? ORDER BY created_at DESC LIMIT 100",campaignId).stream().map(db::output).toList());
        return result;
    }
    public Map<String,Object> createBatch(String campaignId,MarketingDtos.Batch input) {
        return db.tx(()->{
            var c=campaign(campaignId,true);
            boolean image=!input.kind().equals("COPY"),copy=!input.kind().equals("IMAGE");
            JsonNode movies=db.parse(str(c,"movie_snapshot")),ratios=db.parse(str(c,"ratios"));
            int images=image?(input.imageCount()==null?ratios.size():input.imageCount()):0;
            if(!image&&input.imageCount()!=null) throw new BusinessException(400,"文案任务无需设置海报张数");
            var content=input.posterContent();
            if(image&&(content==null||content.movieId()==null||content.movieId()<1||content.title()==null||content.title().isBlank()
                ||content.title().length()>60||content.copy()==null||content.copy().isBlank()||content.copy().length()>300))
                throw new BusinessException(400,"请先在编辑发布内容中选择影片并填写标题和宣传文案，再生成海报");
            if(!image&&content!=null) throw new BusinessException(400,"生成候选文案无需提交海报内容");
            JsonNode posterContent=image?db.parse(db.stringify(Map.of("movieId",content.movieId(),"title",content.title().trim(),"copy",content.copy().trim()))):null;
            if(image) snapshotMovie(c,content.movieId());
            var existing=db.rows("SELECT * FROM marketing_batch WHERE campaign_id=? AND request_key=?",campaignId,input.requestKey());
            if(!existing.isEmpty()) {
                if(!input.kind().equals(str(existing.getFirst(),"kind"))||num(existing.getFirst(),"image_count")!=images)
                    throw new BusinessException(409,"同一请求标识不能用于不同类型或张数的批次");
                if(image) {
                    var tasks=db.rows("SELECT result FROM aigc_task WHERE batch_id=? AND kind='IMAGE' ORDER BY id LIMIT 1",str(existing.getFirst(),"id"));
                    if(tasks.isEmpty()||str(tasks.getFirst(),"result").isBlank()||!posterContent.equals(db.parse(str(tasks.getFirst(),"result")).path("posterContent")))
                        throw new BusinessException(409,"宣传文案或关联影片已变化，请使用新的请求标识生成海报");
                }
                return db.output(existing.getFirst());
            }
            if(image&&(images<1||images>settings.getMaxImagesPerBatch()))
                throw new BusinessException(400,"海报生成张数应为 1–"+settings.getMaxImagesPerBatch()+" 张");
            if(image&&!settings.imageConfigured()) throw new BusinessException(400,"请先配置生图服务");
            if(!text.configured()) throw new BusinessException(400,image?"请先配置语言模型服务，用于生成绘图指令":"请先配置文案模型服务");
            db.one("SELECT id FROM cinema WHERE id=? FOR UPDATE",num(c,"cinema_id"));
            reserveImages(c,images);
            String batchId=id(); LocalDateTime now=LocalDateTime.now();
            db.update("INSERT INTO marketing_batch(id,campaign_id,request_key,kind,image_count,created_at) VALUES(?,?,?,?,?,?)",batchId,campaignId,input.requestKey(),input.kind(),images,now);
            if(copy) for(JsonNode movie:movies) {
                movieFacts(movie.path("id").asLong());
                addTask(campaignId,batchId,movie.path("id").asLong(),"COPY",null,null,now,null);
            }
            // All posters in this batch use the selected movie and the submitted editor snapshot.
            if(image) movieFacts(content.movieId());
            for(int i=0;i<images;i++) {
                JsonNode ratio=ratios.get(i%ratios.size());
                addTask(campaignId,batchId,content.movieId(),"IMAGE","自由创作",ratio.asText(),now,db.stringify(Map.of("posterContent",posterContent)));
            }
            log(campaignId,null,"BATCH_CREATE",input.kind()+" / "+batchId);
            return Map.of("id",batchId,"imageCount",images,"copyCount",copy?movies.size():0);
        });
    }
    private void addTask(String campaignId,String batchId,long movieId,String kind,String style,String ratio,LocalDateTime now,String result) {
        db.update("INSERT INTO aigc_task(id,campaign_id,batch_id,movie_id,kind,style,ratio,status,created_at,updated_at,result) VALUES(?,?,?,?,?,?,?,'QUEUED',?,?,?)",
            id(),campaignId,batchId,movieId,kind,style,ratio,now,now,result);
    }
    private void reserveImages(Map<String,Object> c,int count) {
        if(count==0) return;
        long used=db.scalar("SELECT COUNT(*) FROM marketing_review_log r JOIN marketing_campaign c ON c.id=r.campaign_id WHERE c.cinema_id=? AND r.action='IMAGE_RESERVE' AND r.created_at>=?",
            Long.class,num(c,"cinema_id"),LocalDate.now().atStartOfDay());
        if(used+count>settings.getDailyImageLimit()) throw new BusinessException(429,"该影院今天的图片任务额度已用完");
        for(int i=0;i<count;i++) log(str(c,"id"),null,"IMAGE_RESERVE","图片调用额度预留");
    }
    public void retry(String taskId,MarketingDtos.Retry request) {
        db.tx(()->{
            var task=db.one("SELECT * FROM aigc_task WHERE id=? FOR UPDATE",taskId);
            var c=campaign(str(task,"campaign_id"),true);
            String status=str(task,"status");
            if(!Set.of("FAILED","SUBMIT_UNKNOWN").contains(status)) throw new BusinessException(409,"仅失败或待核实任务可重新处理");
            if(status.equals("SUBMIT_UNKNOWN")&&!request.acknowledgePossibleCharge())
                throw new BusinessException(409,"供应商可能已受理，请核实并确认可能再次计费后重新生成");
            if(num(task,"attempts")>=5) throw new BusinessException(409,"该任务已达到重试上限，请检查服务配置");
            if(str(task,"kind").equals("IMAGE")&&str(task,"remote_url").isBlank()) {
                if(!settings.imageConfigured()) throw new BusinessException(400,"请先配置生图服务");
                if((str(task,"result").isBlank()||db.parse(str(task,"result")).path("prompt").asText().isBlank())&&!text.configured()) throw new BusinessException(400,"请先配置语言模型服务，用于生成绘图指令");
                db.one("SELECT id FROM cinema WHERE id=? FOR UPDATE",num(c,"cinema_id")); reserveImages(c,1);
            }
            db.update("UPDATE aigc_task SET status='QUEUED',lease_token=NULL,lease_until=NULL,error_code=NULL,error_message=NULL,updated_at=? WHERE id=?",LocalDateTime.now(),taskId);
            log(str(c,"id"),null,"TASK_RETRY",taskId);
            return null;
        });
    }
    public Map<String,Object> saveVersion(String campaignId,MarketingDtos.Version input) {
        return db.tx(()->{
            var c=campaign(campaignId,true); snapshotMovie(c,input.movieId());
            var a=db.one("SELECT * FROM marketing_asset WHERE id=? AND campaign_id=? AND movie_id=?",input.assetId(),campaignId,input.movieId());
            var movie=movieFacts(input.movieId()); var cinema=cinemaFacts(num(c,"cinema_id"));
            String assetId;
            try { assetId=str(a,"source_type").equals("MODEL_POSTER")?str(a,"id"):insertAsset(c,input.movieId(),null,str(a,"id"),str(a,"background_key"),str(a,"style"),str(a,"ratio"),str(a,"model"),
                "TEMPLATE_DERIVED",assets.render(str(a,"background_key"),str(a,"ratio"),Objects.toString(movie.get("title")),input.title().trim(),Objects.toString(cinema.get("name")))); }
            catch(Exception e) { throw new BusinessException(400,"海报重新排版失败，请检查素材后重试"); }
            int version=db.scalar("SELECT COALESCE(MAX(version),0)+1 FROM marketing_content_version WHERE campaign_id=?",Integer.class,campaignId);
            String vid=id();
            var payload=Map.of("title",input.title().trim(),"copy",input.copy().trim(),"assetId",assetId,"cta","查看场次");
            String payloadJson=db.stringify(payload);
            db.update("INSERT INTO marketing_content_version(id,campaign_id,movie_id,version,payload,facts_snapshot,content_hash,status,created_at) VALUES(?,?,?,?,?,?,?,'DRAFT',?)",
                vid,campaignId,input.movieId(),version,payloadJson,db.stringify(Map.of("movie",movie,"cinema",cinema)),
                MarketingAssets.hash(payloadJson.getBytes(StandardCharsets.UTF_8)),LocalDateTime.now());
            log(campaignId,vid,"VERSION_CREATE","创建第 "+version+" 版");
            return db.output(db.one("SELECT * FROM marketing_content_version WHERE id=?",vid));
        });
    }
    public void submit(String versionId) {
        db.tx(()->{
            var v=version(versionId); campaign(str(v,"campaign_id"),false);
            if(!str(v,"status").equals("DRAFT")) throw new BusinessException(409,"只有草稿可以提交审核，修改内容请另存新版本");
            validateFacts(v);
            db.update("UPDATE marketing_content_version SET status='PENDING_REVIEW' WHERE id=?",versionId);
            log(str(v,"campaign_id"),versionId,"SUBMIT_REVIEW","提交审核"); return null;
        });
    }
    public void review(String versionId,MarketingDtos.Review request) {
        UserContext.requirePlatformAdmin();
        db.tx(()->{
            var v=version(versionId);
            if(!str(v,"status").equals("PENDING_REVIEW")) throw new BusinessException(409,"该版本不在待审核状态");
            if(!request.approve()&&(request.reason()==null||request.reason().isBlank())) throw new BusinessException(400,"请填写退回原因");
            if(request.approve()) validateFacts(v);
            db.update("UPDATE marketing_content_version SET status=? WHERE id=?",request.approve()?"APPROVED":"REJECTED",versionId);
            log(str(v,"campaign_id"),versionId,request.approve()?"APPROVE":"REJECT",request.reason()); return null;
        });
    }
    public Map<String,Object> publish(String versionId,MarketingDtos.Publish request) {
        UserContext.requirePlatformAdmin();
        return db.tx(()->{
            var v=version(versionId); var c=campaign(str(v,"campaign_id"),false);
            var existing=db.rows("SELECT * FROM marketing_publication WHERE idempotency_key=? OR (content_version_id=? AND slot=?)",request.idempotencyKey(),versionId,request.slot());
            if(!existing.isEmpty()) {
                var p=existing.getFirst();
                if(!versionId.equals(str(p,"content_version_id"))||!request.slot().equals(str(p,"slot"))) throw new BusinessException(409,"发布请求标识已用于其他内容");
                if(str(p,"status").equals("OFFLINE")) throw new BusinessException(409,"该发布已撤下，请创建并审核新版本");
                return db.output(p);
            }
            if(!Set.of("APPROVED","PUBLISHED").contains(str(v,"status"))) throw new BusinessException(409,"只有审核通过的具体版本可以发布");
            LocalDateTime start=request.startAt()==null?LocalDateTime.now():request.startAt(),end=request.endAt();
            if(!end.isAfter(start)||!end.isAfter(LocalDateTime.now())||end.isAfter(LocalDateTime.now().plusDays(90)))
                throw new BusinessException(400,"展示截止时间必须晚于开始时间和当前时间，且不超过 90 天");
            validateFacts(v);
            String pid=id();
            db.update("INSERT INTO marketing_publication(id,content_version_id,campaign_id,cinema_id,slot,start_at,end_at,status,idempotency_key,created_at) VALUES(?,?,?,?,?,?,?,'PUBLISHED',?,?)",
                pid,versionId,str(v,"campaign_id"),num(c,"cinema_id"),request.slot(),start,end,request.idempotencyKey(),LocalDateTime.now());
            db.update("UPDATE marketing_content_version SET status='PUBLISHED' WHERE id=?",versionId);
            log(str(v,"campaign_id"),versionId,"PUBLISH",request.slot());
            return db.output(db.one("SELECT * FROM marketing_publication WHERE id=?",pid));
        });
    }
    public void offline(String publicationId) {
        UserContext.requirePlatformAdmin();
        db.tx(()->{
            var p=db.one("SELECT * FROM marketing_publication WHERE id=? FOR UPDATE",publicationId);
            db.update("UPDATE marketing_publication SET status='OFFLINE' WHERE id=?",publicationId);
            log(str(p,"campaign_id"),str(p,"content_version_id"),"OFFLINE","撤下发布内容"); return null;
        });
    }
    private Map<String,Object> version(String versionId) { return db.one("SELECT * FROM marketing_content_version WHERE id=? FOR UPDATE",versionId); }
    void validateFacts(Map<String,Object> version) {
        var c=db.one("SELECT * FROM marketing_campaign WHERE id=?",str(version,"campaign_id"));
        var facts=Map.of("movie",movieFacts(num(version,"movie_id")),"cinema",cinemaFacts(num(c,"cinema_id")));
        if(!db.parse(str(version,"facts_snapshot")).equals(db.parse(db.stringify(facts))))
            throw new BusinessException(409,"影片或影院资料已变化，请另存新版本并重新审核");
        String aid=db.parse(str(version,"payload")).path("assetId").asText();
        var asset=db.one("SELECT * FROM marketing_asset WHERE id=? AND campaign_id=? AND movie_id=?",aid,str(c,"id"),num(version,"movie_id"));
        if(!Files.isRegularFile(assets.path(str(asset,"storage_key")))) throw new BusinessException(409,"海报素材不可用，请重新制作");
    }
    Map<String,Object> movieFacts(long movieId) {
        return db.one("SELECT id,title,genre,director,actors,description,duration,release_date FROM movie WHERE id=? AND deleted=0 AND status IN (1,2)",movieId);
    }
    Map<String,Object> cinemaFacts(long cinemaId) {
        return db.one("SELECT id,name FROM cinema WHERE id=? AND deleted=0 AND status=1",cinemaId);
    }
    JsonNode snapshotMovie(Map<String,Object> campaign,long movieId) {
        for(JsonNode movie:db.parse(str(campaign,"movie_snapshot"))) if(movie.path("id").asLong()==movieId) return movie;
        throw new BusinessException(400,"影片不属于当前营销需求");
    }
    String insertAsset(Map<String,Object> campaign,long movieId,String taskId,String parent,String background,String style,String ratio,String model,String source,MarketingAssets.Rendered rendered) {
        String aid=id();
        db.update("INSERT INTO marketing_asset(id,campaign_id,cinema_id,movie_id,task_id,parent_asset_id,storage_key,background_key,source_type,model,style,ratio,width,height,checksum,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            aid,str(campaign,"id"),num(campaign,"cinema_id"),movieId,taskId,parent,rendered.key(),background,source,model,style,ratio,rendered.width(),rendered.height(),rendered.checksum(),LocalDateTime.now());
        return aid;
    }
    void log(String campaignId,String versionId,String action,String reason) {
        db.update("INSERT INTO marketing_review_log(id,campaign_id,content_version_id,operator_id,action,reason,created_at) VALUES(?,?,?,?,?,?,?)",
            id(),campaignId,versionId,UserContext.getUserId(),action,reason,LocalDateTime.now());
    }
    public Path privateAsset(String assetId) {
        var a=db.one("SELECT * FROM marketing_asset WHERE id=?",assetId); campaign(str(a,"campaign_id"),false);
        Path path=assets.path(str(a,"storage_key"));
        if(!Files.isRegularFile(path)) throw new BusinessException(404,"素材文件不存在");
        return path;
    }
    public List<Map<String,Object>> placements(String slot,Long cinemaId) {
        if(!Set.of("HOME","CINEMA").contains(slot)||slot.equals("CINEMA")&&cinemaId==null) throw new BusinessException(400,"展示位置无效");
        LocalDateTime now=LocalDateTime.now();
        var rows=db.rows("SELECT p.id,p.cinema_id,p.end_at,v.movie_id,v.payload,c.name AS cinema_name,m.title AS movie_title FROM marketing_publication p JOIN marketing_content_version v ON v.id=p.content_version_id JOIN cinema c ON c.id=p.cinema_id JOIN movie m ON m.id=v.movie_id WHERE p.status='PUBLISHED' AND p.slot=? AND p.start_at<=? AND p.end_at>? AND c.status=1 AND c.deleted=0 AND m.status IN (1,2) AND m.deleted=0"+(slot.equals("CINEMA")?" AND p.cinema_id=?":"")+" ORDER BY p.created_at DESC LIMIT 12",
            slot.equals("CINEMA")?new Object[]{slot,now,now,cinemaId}:new Object[]{slot,now,now});
        return rows.stream().map(r->{
            JsonNode payload=db.parse(str(r,"payload"));
            return Map.<String,Object>of("id",str(r,"id"),"title",payload.path("title").asText(),"copy",payload.path("copy").asText(),
                "imageUrl","/api/marketing/assets/"+payload.path("assetId").asText(),"cinemaId",num(r,"cinema_id"),"cinemaName",str(r,"cinema_name"),
                "movieId",num(r,"movie_id"),"movieTitle",str(r,"movie_title"),"targetUrl","/movie/"+num(r,"movie_id"),
                "endAt",Objects.toString(db.output(r).get("endAt")));
        }).toList();
    }
    public Path publicAsset(String assetId) {
        // Publication payload is authoritative; a guessed asset ID must never reveal an unpublished draft.
        var a=db.one("SELECT * FROM marketing_asset WHERE id=?",assetId);
        LocalDateTime now=LocalDateTime.now();
        var live=db.rows("SELECT v.payload FROM marketing_publication p JOIN marketing_content_version v ON v.id=p.content_version_id JOIN cinema c ON c.id=p.cinema_id JOIN movie m ON m.id=v.movie_id WHERE p.campaign_id=? AND p.status='PUBLISHED' AND p.start_at<=? AND p.end_at>? AND c.status=1 AND c.deleted=0 AND m.status IN (1,2) AND m.deleted=0",
            str(a,"campaign_id"),now,now);
        if(live.stream().noneMatch(v->db.parse(str(v,"payload")).path("assetId").asText().equals(assetId))) throw new BusinessException(404,"素材未公开");
        Path path=assets.path(str(a,"storage_key"));
        if(!Files.isRegularFile(path)) throw new BusinessException(404,"素材不可用");
        return path;
    }
}
