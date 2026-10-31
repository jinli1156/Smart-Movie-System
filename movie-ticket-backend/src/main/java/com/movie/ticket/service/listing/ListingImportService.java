package com.movie.ticket.service.listing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/** Splits uploaded material into independently reviewable drafts; never publishes records. */
@Service @RequiredArgsConstructor @Slf4j
public class ListingImportService {
    private final ListingService workflow;
    private final ListingSourceReader reader;
    private final ListingRecordParser parser;
    private final ListingAiClient ai;
    private final FileUploadService uploads;
    public record Batch(String id,String kind,int total,List<ListingService.Draft> drafts,String extractionMode,String warning) {}
    public Batch create(String kind,Long cinemaId,String text,List<MultipartFile> files) {
        return create(kind,cinemaId,text,files,"AUTO");
    }
    public Batch create(String kind,Long cinemaId,String text,List<MultipartFile> files,String importMode) {
        cinemaId=workflow.scope(kind,cinemaId);
        if (!Set.of("AUTO","TEMPLATE","AI").contains(Objects.toString(importMode,""))) throw new BusinessException(400,"导入方式无效，请选择模板导入或 AI 导入");
        boolean aiImport=importMode.equals("AI");
        if (importMode.equals("TEMPLATE") && files!=null && files.stream().anyMatch(file->!Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|csv|json)$")))
            throw new BusinessException(400,"模板导入仅支持 Excel、CSV、JSON，原始文档或图片请使用 AI 导入");
        var source=reader.read(text,files,kind,aiImport);
        List<ObjectNode> records=aiImport?null:parser.structured(kind,source);
        if (records==null && importMode.equals("TEMPLATE")) throw new BusinessException(400,"未识别到模板字段，请下载对应模板填写，或切换到 AI 导入读取原始资料");
        String mode="STRUCTURED",warning="";
        if (records==null) {
            boolean useVision=!source.images().isEmpty();
            if (useVision && !ai.visionConfigured() && ai.textConfigured() && !source.text().isBlank()) {
                useVision=false; warning="视觉模型未配置，仅拆分和提取文字资料，图片已保留供核对。";
            }
            String unavailable=useVision&&!ai.visionConfigured()?"视觉模型未配置":!useVision&&!ai.textConfigured()?"文字模型未配置":"";
            if (!unavailable.isEmpty()) {
                if (aiImport) throw new BusinessException(400,unavailable+"，暂时无法使用 AI 导入；请配置识别服务或切换到模板导入。");
                records=new ArrayList<>(List.of(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())); mode="MANUAL";
                warning=unavailable+"，无法自动拆分资料；已保存一份原始资料草稿，请人工补充或配置模型后重新导入。";
            } else try {
                var result=ai.extract(kind,useVision?source:new ListingSourceReader.Source(source.text(),source.names(),List.of(),source.imageFiles()));
                if (result.path("error").asText().equals("TOO_MANY_RECORDS")) throw new BusinessException(400,"每批最多识别 20 条资料，请拆分文件后上传");
                records=parser.records(kind,result); mode=useVision?"AI_VISION":"AI_TEXT";
            } catch (BusinessException e) { throw e; }
            catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("Listing batch extraction failed kind={} errorType={}",kind,e.getClass().getSimpleName());
                if (aiImport) throw new BusinessException(502,"AI 识别暂时失败，请稍后重试。原始资料尚未生成草稿。");
                records=new ArrayList<>(List.of(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())); mode="MANUAL";
                warning="AI 识别暂时失败，无法确认条目是否完整；已保存一份原始资料草稿，请稍后重新导入或人工补充。";
            }
        }
        if (records.size()>1 && !source.imageFiles().isEmpty()) warning+=(warning.isBlank()?"":" ")+"本次含多条资料，图片仅作为共有原始资料保留，请逐条核对并设置海报或影院图片。";
        List<String> urls=new ArrayList<>();
        try { for (MultipartFile image:source.imageFiles()) urls.add(uploads.uploadImage(image).get("url")); }
        catch (Exception e) { throw new BusinessException(400,"原始图片保存失败，请重新上传"); }
        String id=UUID.randomUUID().toString();
        var drafts=workflow.createImported(kind,cinemaId,id,records,source,urls,mode,warning);
        return new Batch(id,kind,drafts.size(),drafts,mode,warning);
    }
}
