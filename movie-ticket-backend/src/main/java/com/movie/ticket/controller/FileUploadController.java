package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/image")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        log.info("收到文件上传请求: name={}, size={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        try {
            Map<String, String> result = fileUploadService.uploadImage(file);
            return Result.success("上传成功", result);
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }
}
