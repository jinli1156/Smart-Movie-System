package com.movie.ticket.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AssistantChatVO {
    private String answer;
    private List<String> suggestions;
    private String source;
}
