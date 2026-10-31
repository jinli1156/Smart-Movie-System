package com.movie.ticket.service.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.movie.ticket.exception.BusinessException;
import com.movie.ticket.service.HallSeatLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** A single allowlist shared by extraction, the editor, and publication validation. */
public final class ListingSchema {
    private ListingSchema() {}
    public record Field(String key, String label, String type, boolean required, int maxLength) {}
    private static Field f(String key, String label, String type, boolean required, int max) { return new Field(key,label,type,required,max); }
    public static List<Field> fields(String kind) {
        return switch (kind) {
            case "MOVIE" -> List.of(f("title","电影名称","text",true,100), f("englishTitle","英文名称","text",false,100),
                f("director","导演","text",false,100), f("actors","主演","text",false,255), f("genre","类型","text",true,50),
                f("country","国家/地区","text",false,50), f("language","语言","text",false,50), f("duration","时长（分钟）","integer",true,0),
                f("releaseDate","上映日期","date",false,0), f("poster","电影海报","image",false,255),
                f("trailer","预告片地址","url",false,255), f("description","剧情简介","textarea",true,10000), f("status","发布状态","status",true,0));
            case "CINEMA" -> List.of(f("name","影院名称","text",true,100), f("address","详细地址","text",true,255),
                f("phone","联系电话","text",false,20), f("lng","经度","decimal",false,0), f("lat","纬度","decimal",false,0),
                f("description","影院介绍","textarea",false,10000), f("images","影院图片（JSON 数组）","textarea",false,10000), f("status","发布状态","status",true,0));
            case "HALL" -> List.of(f("name","影厅名称","text",true,50), f("totalRows","座位行数","integer",true,0),
                f("totalCols","座位列数","integer",true,0), f("seatLayout","座位布局（JSON）","textarea",true,20000), f("status","发布状态","status",true,0));
            default -> throw new BusinessException(400,"不支持的上架类型");
        };
    }
    public static ObjectNode clean(String kind, JsonNode input, ObjectMapper json) {
        if (input == null || !input.isObject() || input.toString().length() > 50000) throw new BusinessException(400,"草稿字段格式无效或过长");
        ObjectNode result = json.createObjectNode();
        for (Field f : fields(kind)) {
            JsonNode v = input.get(f.key());
            if (v == null || v.isNull()) continue;
            if (v.isContainerNode()) {
                if (Set.of("seatLayout","images").contains(f.key())) result.put(f.key(), v.toString());
                else if (v.isArray() && Set.of("genre","actors").contains(f.key())) {
                    List<String> values = new ArrayList<>(); v.forEach(item -> { if (item.isTextual()) values.add(item.asText()); });
                    result.put(f.key(),String.join("、",values));
                }
            } else result.set(f.key(), v.isTextual() ? json.getNodeFactory().textNode(v.asText().strip()) : v);
        }
        return result;
    }
    public static List<String> issues(String kind, JsonNode data, ObjectMapper json) {
        List<String> errors = new ArrayList<>();
        for (Field f : fields(kind)) {
            String value = data.path(f.key()).asText("");
            if (value.isBlank()) { if (f.required()) errors.add("请补充"+f.label()); continue; }
            if (f.maxLength() > 0 && value.length() > f.maxLength()) errors.add(f.label()+"不能超过 "+f.maxLength()+" 字");
            try {
                switch (f.type()) {
                    case "integer" -> {
                        int n = Integer.parseInt(value); int max = kind.equals("HALL") ? 50 : 600;
                        if (n < 1 || n > max) errors.add(f.label()+"须为 1-"+max+" 的整数");
                    }
                    case "decimal" -> {
                        BigDecimal n = new BigDecimal(value); int max = f.key().equals("lat") ? 90 : 180;
                        if (n.abs().compareTo(BigDecimal.valueOf(max)) > 0 || n.scale() > 7) errors.add(f.label()+"超出范围或超过 7 位小数");
                    }
                    case "date" -> LocalDate.parse(value);
                    case "status" -> { int n = Integer.parseInt(value); if (n < 0 || n > (kind.equals("MOVIE")?2:1)) errors.add("发布状态无效"); }
                    case "image", "url" -> { if (!safeUrl(value)) errors.add(f.label()+"须为站内上传地址或 HTTP(S) 地址"); }
                    default -> {}
                }
            } catch (RuntimeException e) { errors.add(f.label()+"格式不正确"); }
        }
        if (kind.equals("CINEMA")) {
            if (data.path("lng").asText("").isBlank() != data.path("lat").asText("").isBlank()) errors.add("经度和纬度需同时填写");
            if (!data.path("images").asText("").isBlank()) try {
                JsonNode images = json.readTree(data.path("images").asText());
                if (!images.isArray() || images.size() > 10) throw new IllegalArgumentException();
                for (JsonNode image : images) if (!image.isTextual() || !safeUrl(image.asText())) throw new IllegalArgumentException();
            } catch (Exception e) { errors.add("影院图片须为最多 10 个 HTTP(S) 或站内图片地址的 JSON 数组"); }
        }
        if (kind.equals("HALL") && !data.path("seatLayout").asText("").isBlank()) try {
            JsonNode layout = json.readTree(data.path("seatLayout").asText());
            if (layout == null || !layout.isObject() && !layout.isArray()) throw new IllegalArgumentException();
            HallSeatLayout.parse(data.path("seatLayout").asText(), Integer.parseInt(data.path("totalRows").asText()), Integer.parseInt(data.path("totalCols").asText()));
        } catch (Exception e) { errors.add("座位布局与行列数不匹配，或过道配置无效"); }
        return errors;
    }
    private static boolean safeUrl(String value) {
        if (value.matches("/api/uploads/[a-zA-Z0-9_.-]+")) return true;
        try { var uri = java.net.URI.create(value); return Set.of("https","http").contains(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null; }
        catch (Exception e) { return false; }
    }
}
