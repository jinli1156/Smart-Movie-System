package com.movie.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 助手上下文中的单条消息，记录角色与文本内容。 */
@Data
public class AssistantMessage {

    @NotBlank
    @Pattern(regexp = "user|assistant")
    private String role;

    @NotBlank
    @Size(max = 4000)
    private String content;
}
