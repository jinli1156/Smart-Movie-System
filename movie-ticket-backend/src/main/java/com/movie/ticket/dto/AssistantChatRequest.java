package com.movie.ticket.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 助手对话输入，携带问题、历史、会话标识及本轮观影条件变更。 */
@Data
public class AssistantChatRequest {
    // Guest state is untrusted input, validated like tool arguments; logged-in requests ignore it.
    @Size(max = 16000)
    private String guestPlan;

    private com.fasterxml.jackson.databind.JsonNode planPatch;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.fasterxml.jackson.databind.JsonNode watchPlan;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.fasterxml.jackson.databind.JsonNode savedPreferences;

    @Pattern(regexp = "[0-9a-fA-F-]{36}")
    private String conversationId;

    @Pattern(regexp = "[0-9a-fA-F-]{36}")
    private String clientMessageId;

    @NotBlank(message = "请输入问题")
    @Size(max = 500, message = "问题不能超过500字")
    private String message;

    @Valid
    @Size(max = 10)
    private List<@NotNull AssistantMessage> history = new ArrayList<>();
}
