package com.movie.ticket.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 助手回答视图，封装回答文本、工具结果、来源和后续建议。 */
@Data
public class AssistantChatVO {
    private com.fasterxml.jackson.databind.JsonNode watchPlan;
    private String conversationId;
    private String answer;
    private List<String> suggestions;
    private String source;
    private List<Map<String, Object>> toolResults;

    public AssistantChatVO(String answer, List<String> suggestions, String source, List<Map<String, Object>> toolResults) {
        this.answer = answer; this.suggestions = suggestions; this.source = source; this.toolResults = toolResults;
    }
}
