package com.movie.ticket.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantChatRequest {

    @NotBlank(message = "请输入问题")
    @Size(max = 500, message = "问题不能超过500字")
    private String message;

    @Valid
    @Size(max = 10)
    private List<AssistantMessage> history = new ArrayList<>();
}
