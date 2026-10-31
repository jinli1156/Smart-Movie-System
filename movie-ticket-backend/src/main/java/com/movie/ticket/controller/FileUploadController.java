package com.movie.ticket.controller;

import com.movie.ticket.common.Result;
import com.movie.ticket.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** 图片上传接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /** 检查并保存图片，返回可供页面展示的资源地址。 */
    @PostMapping("/image")
    @PreAuthorize("hasAnyRole('ADMIN', 'CINEMA_ADMIN')")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("收到文件上传请求: name={}, size={}, contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        Map<String, String> result = fileUploadService.uploadImage(file);
        return Result.success("上传成功", result);
    }
}
