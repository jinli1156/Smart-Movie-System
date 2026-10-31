package com.movie.ticket.service.listing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "listing")
public class ListingSettings {
    private String visionApiKey = "";
    private String visionBaseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String visionModel = "glm-4.6v-flash";
    private String proxyUrl = "http://127.0.0.1:7890";
}
