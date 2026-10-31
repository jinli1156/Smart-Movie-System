package com.movie.ticket.service.poster;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "poster")
public class PosterSettings {
    private boolean workerEnabled = true;
    private int dailyLimit = 6;
    private String storagePath = "../.local/personal-poster-assets";
}
