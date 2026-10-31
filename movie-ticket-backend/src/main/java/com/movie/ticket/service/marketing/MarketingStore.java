package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;

@Repository
public class MarketingStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    public MarketingStore(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.json=json; this.transaction=new TransactionTemplate(manager);
    }
    public <T> T tx(Supplier<T> action) { return transaction.execute(s -> action.get()); }
    public List<Map<String,Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql,args).stream().map(row -> {
            Map<String,Object> normalized=new LinkedHashMap<>();
            row.forEach((k,v)->normalized.put(k.toLowerCase(Locale.ROOT),
                v instanceof java.sql.Date d ? d.toLocalDate().toString() : v));
            return normalized;
        }).toList();
    }
    public Map<String,Object> one(String sql, Object... args) {
        var list=rows(sql,args);
        if(list.isEmpty()) throw new BusinessException(404,"营销记录不存在或无权访问");
        return list.getFirst();
    }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
    public <T> T scalar(String sql,Class<T> type,Object... args) { return jdbc.queryForObject(sql,type,args); }
    public String stringify(Object value) {
        try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("营销数据无法序列化"); }
    }
    public JsonNode parse(String value) {
        try { return json.readTree(value); } catch(Exception e) { throw new IllegalStateException("营销数据格式无效"); }
    }
    static String str(Map<String,Object> row,String key) { return Objects.toString(row.get(key),""); }
    static long num(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    static String id() { return UUID.randomUUID().toString(); }
    public Map<String,Object> output(Map<String,Object> row) {
        var out=new LinkedHashMap<String,Object>();
        row.forEach((key,value) -> {
            String lower=key.toLowerCase(Locale.ROOT);
            if(Set.of("remote_url","lease_token","storage_key","background_key").contains(lower)) return;
            StringBuilder camel=new StringBuilder(); boolean upper=false;
            for(char c:lower.toCharArray()) { if(c=='_') upper=true; else { camel.append(upper?Character.toUpperCase(c):c); upper=false; } }
            Object converted=value;
            if(value instanceof java.sql.Timestamp t) converted=t.toLocalDateTime().toString();
            if(value instanceof java.sql.Clob c) { try { converted=c.getSubString(1,(int)c.length()); } catch(Exception e) { throw new IllegalStateException(e); } }
            if(converted instanceof String s && Set.of("styles","ratios","movie_snapshot","result","payload","facts_snapshot").contains(lower) && !s.isBlank()) converted=parse(s);
            out.put(camel.toString(),converted);
        });
        return out;
    }
}
