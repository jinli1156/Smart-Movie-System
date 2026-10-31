package com.movie.ticket.service.souvenir;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "souvenir")
public class SouvenirSettings {
    private boolean workerEnabled = true;
    private String storagePath = "../.local/souvenir-assets";
    private int dailyLimit = 6;
}
