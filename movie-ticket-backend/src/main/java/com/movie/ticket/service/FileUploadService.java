package com.movie.ticket.service;

import com.movie.ticket.config.FileUploadConfig;
import com.movie.ticket.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 图片上传业务：检查文件类型、大小及存储路径，生成前端可使用的图片地址。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final FileUploadConfig fileUploadConfig;

    /** 检查并保存图片，返回可供页面展示的资源地址。 */
    public Map<String, String> uploadImage(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }

        if (file.getSize() > fileUploadConfig.getMaxSize()) {
            throw new BusinessException(400,
                    "文件大小不能超过" + (fileUploadConfig.getMaxSize() / 1024 / 1024) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException(400, "文件名不能为空");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        boolean allowed = false;
        for (String type : fileUploadConfig.getAllowedTypes()) {
            if (type.equals(extension)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new BusinessException(400,
                    "不支持的文件格式，允许的格式: jpg, jpeg, png, gif, webp, bmp");
        }

        String newFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        // 使用绝对路径，确保在项目根目录下创建 uploads 目录
        String basePath = System.getProperty("user.dir");
        Path uploadPath = Paths.get(basePath, fileUploadConfig.getPath());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("创建上传目录: {}", uploadPath.toAbsolutePath());
        }

        Path filePath = uploadPath.resolve(newFilename);
        file.transferTo(filePath.toFile());

        String accessPrefix = fileUploadConfig.getAccessPrefix();
        if (!accessPrefix.endsWith("/")) {
            accessPrefix = accessPrefix + "/";
        }
        String accessUrl = accessPrefix + newFilename;

        log.info("文件上传成功: {} -> {} (保存到: {}, 访问URL: {})",
                originalFilename, newFilename, filePath.toAbsolutePath(), accessUrl);

        Map<String, String> result = new HashMap<>();
        result.put("url", accessUrl);
        result.put("filename", originalFilename);
        result.put("newFilename", newFilename);
        return result;
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
