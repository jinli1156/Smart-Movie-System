package com.movie.ticket;

import com.movie.ticket.config.FileUploadConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot 启动入口，启用应用配置及组件扫描。 */
@SpringBootApplication
@MapperScan("com.movie.ticket.mapper")
@EnableScheduling
@EnableConfigurationProperties(FileUploadConfig.class)
public class MovieTicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieTicketApplication.class, args);
    }
}
