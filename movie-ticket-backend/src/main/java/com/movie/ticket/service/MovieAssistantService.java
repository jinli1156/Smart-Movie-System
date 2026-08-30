package com.movie.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.movie.ticket.dto.AssistantChatRequest;
import com.movie.ticket.dto.AssistantMessage;
import com.movie.ticket.entity.Movie;
import com.movie.ticket.mapper.MovieMapper;
import com.movie.ticket.vo.AssistantChatVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieAssistantService {

    private final MovieMapper movieMapper;

    @Value("${assistant.api-key:}")
    private String apiKey;

    @Value("${assistant.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${assistant.model:qwen3.8-flash}")
    private String model;

    @Value("${assistant.reasoning-effort:low}")
    private String reasoningEffort;

    @Value("${assistant.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${assistant.read-timeout-ms:60000}")
    private int readTimeoutMs;

    public AssistantChatVO chat(AssistantChatRequest request) {
        List<Movie> movies = movieMapper.selectList(new LambdaQueryWrapper<Movie>()
                .in(Movie::getStatus, 1, 2)
                .orderByDesc(Movie::getRating)
                .last("LIMIT 30"));

        if (StringUtils.hasText(apiKey)) {
            try {
                String answer = askModel(request, movies);
                if (StringUtils.hasText(answer)) {
                    return new AssistantChatVO(answer, suggestions(request.getMessage()), "model");
                }
            } catch (RestClientResponseException e) {
                log.warn("智能电影助手调用千问失败（HTTP {}），已切换到本地知识库", e.getStatusCode().value());
            } catch (Exception e) {
                // 不记录请求头、请求体或异常详情，避免凭据和对话内容进入日志。
                log.warn("智能电影助手调用千问失败（{}），已切换到本地知识库", e.getClass().getSimpleName());
            }
        }

        return new AssistantChatVO(localAnswer(request.getMessage(), movies),
                suggestions(request.getMessage()), "local");
    }

    @SuppressWarnings("unchecked")
    private String askModel(AssistantChatRequest request, List<Movie> movies) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(movies)));
        List<AssistantMessage> history = request.getHistory() == null ? List.of() : request.getHistory();
        history.stream().skip(Math.max(0, history.size() - 6L))
                .forEach(item -> messages.add(Map.of("role", item.getRole(), "content", item.getContent())));
        messages.add(Map.of("role", "user", "content", request.getMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.5);
        body.put("max_tokens", 1000);
        body.put("reasoning_effort", reasoningEffort);

        Map<String, Object> response = client.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) return null;
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return null;
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null || message.get("content") == null) return null;
        return String.valueOf(message.get("content"));
    }

    private String systemPrompt(List<Movie> movies) {
        String catalog = movies.stream().limit(20).map(this::movieLine).collect(Collectors.joining("\n"));
        return "你是 CINEMA+ 智能电影小助手。使用简洁、友好的中文回答电影知识、影片推荐、观影格式和购票相关问题。"
                + "推荐时优先参考下面的本系统片单；不确定的信息要明确说明，不要编造场次、票价或主创信息。\n当前片单：\n" + catalog;
    }

    private String localAnswer(String rawQuestion, List<Movie> movies) {
        String question = rawQuestion.trim();
        String lower = question.toLowerCase();
        Movie matched = movies.stream().filter(movie ->
                question.contains(movie.getTitle()) ||
                        (StringUtils.hasText(movie.getEnglishTitle()) && lower.contains(movie.getEnglishTitle().toLowerCase())))
                .findFirst().orElse(null);
        if (matched != null) return describeMovie(matched);

        if (containsAny(question, "你好", "您好", "嗨", "在吗")) {
            return "你好，我是 CINEMA+ 智能电影小助手。你可以问我正在热映、影片推荐、导演演员、剧情类型，以及 IMAX、杜比影院等观影知识。";
        }
        if (containsAny(question, "即将上映", "待映", "什么时候上映")) {
            return listMovies("即将上映", movies.stream().filter(m -> Integer.valueOf(2).equals(m.getStatus())).limit(5).toList());
        }
        if (containsAny(question, "正在热映", "热映", "现在有什么电影")) {
            return listMovies("正在热映", movies.stream().filter(m -> Integer.valueOf(1).equals(m.getStatus())).limit(6).toList());
        }
        if (containsAny(question, "推荐", "好看", "看什么", "高分")) {
            return listMovies("为你推荐", movies.stream()
                    .filter(m -> Integer.valueOf(1).equals(m.getStatus()))
                    .sorted((a, b) -> ratingOf(b).compareTo(ratingOf(a))).limit(5).toList());
        }
        if (lower.contains("imax")) {
            return "IMAX 通过更大的银幕、专用放映系统和增强声场提升沉浸感。视觉奇观、科幻、动作类影片通常更适合 IMAX；购票时还要确认影片是否使用 IMAX 规格制作。";
        }
        if (containsAny(question, "杜比", "dolby")) {
            return "杜比影院通常结合 Dolby Vision 高动态范围画面与 Dolby Atmos 全景声，优势是黑位、对比度和声音定位。重视音效、暗场细节时可以优先选择。";
        }
        if (containsAny(question, "cinity", "CINITY")) {
            return "CINITY 是融合高亮度、高动态范围、高帧率与沉浸声的影院放映系统，适合动作场面多、画面运动快速的影片。";
        }
        if (containsAny(question, "科幻", "动作", "动画", "剧情", "喜剧", "惊悚")) {
            List<Movie> found = movies.stream().filter(m -> StringUtils.hasText(m.getGenre()) && question.contains(m.getGenre()))
                    .sorted((a, b) -> ratingOf(b).compareTo(ratingOf(a))).limit(5).toList();
            if (!found.isEmpty()) return listMovies("符合你偏好的影片", found);
        }
        if (containsAny(question, "评分", "豆瓣", "烂番茄", "imdb")) {
            return "电影评分适合用来初筛，但不同平台的用户结构和评分标准不同。建议同时参考题材、导演、影评关键词和自己的观影偏好，不必只看单一分数。";
        }
        return "这个问题我暂时无法从本地片单中确定答案。你可以换一种问法，例如“推荐几部正在热映的高分电影”“IMAX 和杜比影院有什么区别”或直接输入片名。配置 DASHSCOPE_API_KEY 后，我还能通过千问回答更广泛的电影知识。";
    }

    private String describeMovie(Movie movie) {
        StringBuilder answer = new StringBuilder("《").append(movie.getTitle()).append("》");
        if (StringUtils.hasText(movie.getGenre())) answer.append("是一部").append(movie.getGenre()).append("影片");
        if (StringUtils.hasText(movie.getDirector())) answer.append("，导演是").append(movie.getDirector());
        if (movie.getDuration() != null) answer.append("，片长约").append(movie.getDuration()).append("分钟");
        if (ratingOf(movie).compareTo(BigDecimal.ZERO) > 0) answer.append("，当前评分").append(ratingOf(movie));
        answer.append("。");
        if (StringUtils.hasText(movie.getDescription())) answer.append(movie.getDescription());
        return answer.toString();
    }

    private String listMovies(String title, List<Movie> list) {
        if (list.isEmpty()) return "当前没有查询到" + title + "影片，你可以稍后刷新片单再试。";
        String items = list.stream().map(movie -> "《" + movie.getTitle() + "》"
                        + (ratingOf(movie).compareTo(BigDecimal.ZERO) > 0 ? " " + ratingOf(movie) + "分" : ""))
                .collect(Collectors.joining("、"));
        return title + "：" + items + "。你可以继续输入片名，了解导演、类型、片长和剧情简介。";
    }

    private String movieLine(Movie movie) {
        return movie.getTitle() + " | " + movie.getGenre() + " | 导演:" + movie.getDirector()
                + " | 评分:" + ratingOf(movie) + " | 状态:" + (Integer.valueOf(1).equals(movie.getStatus()) ? "热映" : "即将上映");
    }

    private BigDecimal ratingOf(Movie movie) {
        return movie.getRating() == null ? BigDecimal.ZERO : movie.getRating();
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) if (value.toLowerCase().contains(keyword.toLowerCase())) return true;
        return false;
    }

    private List<String> suggestions(String question) {
        if (containsAny(question, "热映", "推荐", "看什么")) {
            return List.of("推荐一部高分科幻片", "IMAX 和杜比有什么区别？", "有哪些即将上映的电影？");
        }
        return List.of("现在有什么电影？", "推荐几部高分电影", "IMAX 适合看什么？");
    }
}
