package com.movie.ticket.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.movie.ticket.dto.SeatPreference;
import com.movie.ticket.vo.SeatRecommendationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 调用模型解析自然语言选座偏好；模型分析结果交由座位推荐服务继续校验。 */
@Slf4j
@Service
public class SeatAiRecommendationService {

    @Value("${assistant.api-key:}")
    private String apiKey;

    @Value("${assistant.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${assistant.model:deepseek-v4-flash}")
    private String model;

    @Value("${assistant.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${assistant.read-timeout-ms:60000}")
    private int readTimeoutMs;

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public String modelName() {
        return model;
    }

    public Optional<SeatAiAnalysis> analyze(String naturalLanguage,
                                            int people,
                                            int rows,
                                            int cols,
                                            List<SeatRecommendationVO> candidates) {
        if (!isConfigured() || !StringUtils.hasText(naturalLanguage) || candidates.isEmpty()) {
            return Optional.empty();
        }
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(connectTimeoutMs);
            requestFactory.setReadTimeout(Math.min(readTimeoutMs, 20000));
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .requestFactory(requestFactory)
                    .build();

            List<Map<String, Object>> candidatePayload = candidates.stream().map(candidate -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("seatLabel", candidate.getSeatLabel());
                item.put("seats", candidate.getSeats());
                item.put("match", candidate.getMatch());
                item.put("scores", candidate.getScores());
                item.put("singleSeatPenalty", candidate.getSingleSeatPenalty());
                item.put("algorithmReason", candidate.getReason());
                return item;
            }).toList();

            String systemPrompt = "你是电影院选座分析器。只能从候选座位块中选择，禁止编造座位号。"
                    + "理解用户自然语言偏好，并严格返回单个 JSON 对象，不要 Markdown。格式："
                    + "{\"preference\":\"CENTER|QUIET|EASY_ACCESS|COUPLE|ACCESSIBLE\","
                    + "\"summary\":\"不超过40字的偏好总结\","
                    + "\"ranking\":[{\"seatLabel\":\"必须与候选完全一致\",\"reason\":\"不超过80字的具体分析\"}]}。"
                    + "ranking 最多返回 5 项，结合结构化得分、留单座惩罚和用户表达排序。";
            String userPrompt = "购票人数：" + people + "；影厅：" + rows + "排×" + cols + "列；用户偏好："
                    + naturalLanguage.trim() + "；候选座位：" + JSON.toJSONString(candidatePayload);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)));
            body.put("temperature", 0.2);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("max_tokens", 1200);
            body.put("thinking", Map.of("type", "disabled"));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post().uri("/chat/completions").body(body)
                    .retrieve().body(Map.class);
            String content = responseContent(response);
            return Optional.ofNullable(parseAnalysis(content));
        } catch (RestClientResponseException exception) {
            log.warn("智能选座调用 DeepSeek 失败（HTTP {}），使用算法解释", exception.getStatusCode().value());
        } catch (RuntimeException exception) {
            log.warn("智能选座模型分析失败（{}），使用算法解释", exception.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private String responseContent(Map<String, Object> response) {
        if (response == null) return null;
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return null;
        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        return message == null || message.get("content") == null ? null : String.valueOf(message.get("content"));
    }

    SeatAiAnalysis parseAnalysis(String content) {
        if (!StringUtils.hasText(content)) return null;
        String json = content.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        JSONObject root = JSON.parseObject(json);
        SeatPreference preference = SeatPreference.from(root.getString("preference"));
        String summary = limited(root.getString("summary"), 80);
        JSONArray ranking = root.getJSONArray("ranking");
        List<RankedReason> reasons = new ArrayList<>();
        if (ranking != null) {
            for (int i = 0; i < Math.min(5, ranking.size()); i++) {
                JSONObject item = ranking.getJSONObject(i);
                String seatLabel = limited(item.getString("seatLabel"), 60);
                String reason = limited(item.getString("reason"), 160);
                if (StringUtils.hasText(seatLabel) && StringUtils.hasText(reason)) {
                    reasons.add(new RankedReason(seatLabel, reason));
                }
            }
        }
        return new SeatAiAnalysis(preference, summary, reasons);
    }

    private String limited(String value, int max) {
        if (!StringUtils.hasText(value)) return "";
        String normalized = value.trim().replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    public record SeatAiAnalysis(SeatPreference preference, String summary, List<RankedReason> ranking) {}
    public record RankedReason(String seatLabel, String reason) {}
}
