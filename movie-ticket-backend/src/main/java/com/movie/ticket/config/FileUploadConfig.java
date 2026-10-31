package com.movie.ticket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 集中读取上传目录与访问地址配置，供文件存储和资源访问使用。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    private String path = "uploads/";

    private String accessPrefix = "/api/uploads/";

    private Long maxSize = 5L * 1024 * 1024;

    private String[] allowedTypes = {"jpg", "jpeg", "png", "gif", "webp", "bmp"};

    /**
     * Spring MVC 资源映射路径（相对于 context-path），不要带 /api 前缀
     */
    public String getResourceMapping() {
        // 去掉 /api 前缀，得到 /uploads/
        if (accessPrefix.startsWith("/api")) {
            return accessPrefix.substring(4);
        }
        return accessPrefix;
    }
}
