package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

/** One explicitly enabled reference-image request, with durable replay protection. */
@EnabledIfEnvironmentVariable(named = "QWEN_IMAGE_LIVE_TEST", matches = "1")
class QwenImageReferenceLiveTest {
    @Test void generatesFromSyntheticReferenceAndSavesTheRealImage() throws Exception {
        Path local = Path.of("../.local").toAbsolutePath().normalize();
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(local.resolve("qwen.properties"))) { properties.load(reader); }
        var settings = new MarketingSettings();
        settings.setImageProvider(properties.getProperty("marketing.image-provider"));
        settings.setImageModel(properties.getProperty("marketing.image-model"));
        settings.setImageBaseUrl(properties.getProperty("marketing.image-base-url"));
        settings.setImageApiKey(properties.getProperty("marketing.image-api-key"));
        settings.setProxyUrl(properties.getProperty("marketing.proxy-url", "http://127.0.0.1:7890"));
        settings.setStoragePath(local.resolve("qwen-live-assets").toString());
        var client = new MarketingImageClient(settings, new ObjectMapper());
        assertThat(client.supportsReferencePhotos()).isTrue();
        String label = settings.getImageModel().replaceAll("[^a-zA-Z0-9_-]", "_");
        Path cache = local.resolve("qwen-reference-" + label + ".result");
        Path marker = local.resolve("qwen-reference-" + label + ".submitted");
        String result;
        if (Files.exists(cache)) result = Files.readString(cache);
        else {
            assertThat(Files.exists(marker)).as("此前请求结果待核实，不自动重复提交付费生图").isFalse();
            var reference = new BufferedImage(384, 512, BufferedImage.TYPE_INT_RGB);
            var g = reference.createGraphics();
            try {
                g.setPaint(new GradientPaint(0, 0, new Color(15, 24, 60), 384, 512, new Color(65, 22, 70)));
                g.fillRect(0, 0, 384, 512); g.setColor(new Color(230, 66, 125));
                g.fillOval(95, 65, 200, 200); g.setColor(new Color(20, 25, 39));
                g.fillOval(169, 150, 50, 60); g.fillRoundRect(143, 205, 98, 174, 35, 35);
                g.fillRect(150, 350, 34, 112); g.fillRect(202, 350, 34, 112);
            } finally { g.dispose(); }
            var bytes = new ByteArrayOutputStream(); ImageIO.write(reference, "jpeg", bytes);
            Files.write(local.resolve("qwen-reference-synthetic.jpg"), bytes.toByteArray());
            Files.writeString(marker, "one reference image request submitted", StandardOpenOption.CREATE_NEW);
            try {
                result = client.generateWithReference("为电影《哪吒》创作赛博朋克风格的竖版观影海报。参考图为合成人物剪影，保留人物居中站立的构图，设计霓虹未来都市、红色能量丝带和悬浮风火轮。主标题为“我的电影时刻”，电影级光影，完整海报。", "3:4", bytes.toByteArray());
            } catch(MarketingImageClient.GenerationException error) {
                if(!error.isUncertain()) Files.deleteIfExists(marker);
                throw error;
            }
            Files.writeString(cache, result, StandardOpenOption.CREATE_NEW);
        }
        Path output = local.resolve("qwen-reference-live.png");
        byte[] imageBytes = client.download(result);
        var image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThan(256); assertThat(image.getHeight()).isGreaterThan(image.getWidth());
        ImageIO.write(image, "png", output.toFile());
        System.out.println("QWEN_REFERENCE_LIVE_OK model=" + settings.getImageModel() + " dimensions=" + image.getWidth() + "x" + image.getHeight());
    }
}
