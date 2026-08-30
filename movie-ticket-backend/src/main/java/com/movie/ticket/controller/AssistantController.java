package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.dto.AssistantChatRequest;
import com.movie.ticket.service.MovieAssistantService;
import com.movie.ticket.vo.AssistantChatVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final MovieAssistantService movieAssistantService;

    @PostMapping("/chat")
    public Result<AssistantChatVO> chat(@Valid @RequestBody AssistantChatRequest request) {
        return Result.success(movieAssistantService.chat(request));
    }
}
