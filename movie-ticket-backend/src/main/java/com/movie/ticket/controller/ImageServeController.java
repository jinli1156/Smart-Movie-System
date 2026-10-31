package com.movie.ticket.controller;

import com.movie.ticket.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 本地图片访问接口入口：接收请求参数并返回统一响应，具体业务处理交给服务层。 */
@Slf4j
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class ImageServeController {

    private final FileUploadConfig fileUploadConfig;

    /** 根据受限文件路径返回本地图片资源。 */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
        try {
            String basePath = System.getProperty("user.dir");
            Path filePath = Paths.get(basePath, fileUploadConfig.getPath(), filename).toAbsolutePath();

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                log.warn("图片不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .body(resource);
        } catch (Exception e) {
            log.error("图片读取失败: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
