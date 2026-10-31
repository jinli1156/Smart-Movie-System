package com.movie.ticket.service.listing;

import cn.hutool.core.text.csv.CsvUtil;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.movie.ticket.exception.BusinessException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.*;

/** Parses every structured record before any draft is written. */
@Component @RequiredArgsConstructor
public class ListingRecordParser {
    public static final int MAX_RECORDS=20;
    private final ObjectMapper json;
    public static Map<String,String> aliases(String kind) {
        Map<String,String> aliases=new HashMap<>();
        for (var field:ListingSchema.fields(kind)) { aliases.put(field.key(),field.key()); aliases.put(field.label(),field.key()); }
        for (var alias:Map.of("时长","duration","电影名","title","简介","description","总行数","totalRows","总列数","totalCols").entrySet())
            if (aliases.containsValue(alias.getValue())) aliases.put(alias.getKey(),alias.getValue());
        return aliases;
    }
    public List<ObjectNode> structured(String kind,ListingSourceReader.Source source) {
        List<ObjectNode> result=new ArrayList<>();
        for (String text:source.textParts()) {
            List<ObjectNode> records=parse(kind,text);
            if (records==null) return null;
            result.addAll(records); limit(result.size());
        }
        return result.isEmpty()?null:result;
    }
    private List<ObjectNode> parse(String kind,String text) {
        String clean=text.strip();
        if (clean.startsWith("{") || clean.startsWith("[")) {
            try { return records(kind,json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(clean)); }
            catch (BusinessException e) { throw e; }
            catch (Exception e) { throw new BusinessException(400,"JSON 格式无效，请检查数组、逗号和括号"); }
        }
        var rows=CsvUtil.getReader().readFromStr(clean).getRows();
        if (rows.size()<2 || rows.getFirst().size()<2) return null;
        var header=rows.getFirst(); Map<String,String> aliases=aliases(kind);
        if (!aliases.containsKey(header.get(0).strip())) return null;
        limit(rows.size()-1); Set<String> seen=new HashSet<>();
        for (String label:header) { String key=aliases.get(label.strip()); if (key!=null && !seen.add(key)) throw new BusinessException(400,"CSV 字段重复："+label); }
        List<ObjectNode> result=new ArrayList<>();
        for (int r=1;r<rows.size();r++) {
            var row=rows.get(r);
            if (row.size()!=header.size()) throw new BusinessException(400,"CSV 第 "+(r+1)+" 行列数与表头不一致，请检查引号和逗号");
            ObjectNode fields=json.createObjectNode();
            for (int c=0;c<header.size();c++) { String key=aliases.get(header.get(c).strip()); if (key!=null) fields.put(key,row.get(c)); }
            result.add(ListingSchema.clean(kind,fields,json));
        }
        return result;
    }
    public List<ObjectNode> records(String kind,JsonNode input) {
        if (input!=null && input.isObject()) for (String wrapper:List.of("records","movies","cinemas","halls","data")) {
            if (input.path(wrapper).isArray()) { input=input.path(wrapper); break; }
        }
        List<JsonNode> records=new ArrayList<>();
        if (input!=null && input.isArray()) input.forEach(records::add); else records.add(input);
        if (records.isEmpty()) throw new BusinessException(400,"资料列表为空，请至少填写一条资料");
        limit(records.size()); List<ObjectNode> result=new ArrayList<>();
        for (int n=0;n<records.size();n++) {
            JsonNode record=records.get(n);
            if (record!=null && record.has("fields")) record=record.get("fields");
            if (record==null || !record.isObject()) throw new BusinessException(400,"第 "+(n+1)+" 条资料须为字段对象");
            ObjectNode fields=ListingSchema.clean(kind,record,json);
            if (fields.isEmpty()) throw new BusinessException(400,"第 "+(n+1)+" 条没有匹配的"+switch(kind) { case "MOVIE" -> "电影"; case "CINEMA" -> "影院"; default -> "影厅"; }+"字段，请核对上架类型");
            result.add(fields);
        }
        return result;
    }
    public static void limit(int size) { if (size>MAX_RECORDS) throw new BusinessException(400,"每批最多识别 "+MAX_RECORDS+" 条资料，请拆分文件后上传"); }
}
