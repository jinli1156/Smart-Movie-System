package com.movie.ticket.service.marketing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "marketing")
public class MarketingSettings {
    private boolean workerEnabled = true;
    private String zhipuApiKey = "";
    private String imageProvider = "zhipu";
    private String imageApiKey = "";
    private String imageModel = "glm-image";
    private String textProvider = "auto";
    private String textModel = "glm-4.7-flash";
    private String imageBaseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String textBaseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private java.util.List<String> imageDownloadHosts = java.util.List.of("hangzhale.com");
    private String proxyUrl = "http://127.0.0.1:7890";
    private String storagePath = "../.local/marketing-assets";
    private int maxImagesPerBatch = 18;
    private int dailyImageLimit = 60;
    private int concurrency = 2;
    private int connectTimeoutSeconds = 60;
    private int requestTimeoutSeconds = 600;
    private int downloadTimeoutSeconds = 120;
    public String effectiveImageApiKey() { return imageApiKey!=null&&!imageApiKey.isBlank()?imageApiKey:"zhipu".equals(imageProvider)?zhipuApiKey:""; }
    public boolean imageConfigured() { return effectiveImageApiKey()!=null&&!effectiveImageApiKey().isBlank()
        &&java.util.Set.of("zhipu","openai-images","qwen-images").contains(imageProvider); }
    public boolean zhipuTextConfigured() { return zhipuApiKey!=null&&!zhipuApiKey.isBlank(); }
}
