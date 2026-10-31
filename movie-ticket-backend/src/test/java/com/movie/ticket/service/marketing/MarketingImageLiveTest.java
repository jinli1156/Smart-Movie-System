package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in. Cache the provider result outside Git so transfer retries do not generate again. */
@EnabledIfEnvironmentVariable(named="MARKETING_LIVE_TEST",matches="1")
class MarketingImageLiveTest {
    @Test void generatesAndSavesOneRealPoster() throws Exception {
        var properties=new Properties();
        try(var reader=Files.newBufferedReader(Path.of("../.local/marketing.properties"))) { properties.load(reader); }
        Path qwen=Path.of("../.local/qwen.properties");
        if(Files.isRegularFile(qwen)) try(var reader=Files.newBufferedReader(qwen)) { properties.load(reader); }
        var settings=new MarketingSettings();
        settings.setZhipuApiKey(properties.getProperty("marketing.zhipu-api-key"));
        settings.setImageProvider(properties.getProperty("marketing.image-provider","zhipu"));
        settings.setImageApiKey(properties.getProperty("marketing.image-api-key",""));
        settings.setImageBaseUrl(properties.getProperty("marketing.image-base-url",settings.getImageBaseUrl()));
        settings.setImageModel(properties.getProperty("marketing.image-model","glm-image"));
        settings.setProxyUrl(properties.getProperty("marketing.proxy-url","http://127.0.0.1:7890"));
        settings.setStoragePath("../.local/marketing-live-assets");
        settings.setConnectTimeoutSeconds(Integer.parseInt(properties.getProperty("marketing.connect-timeout-seconds","60")));
        settings.setRequestTimeoutSeconds(Integer.parseInt(properties.getProperty("marketing.request-timeout-seconds","600")));
        settings.setDownloadTimeoutSeconds(Integer.parseInt(properties.getProperty("marketing.download-timeout-seconds","120")));
        var client=new MarketingImageClient(settings,new ObjectMapper());
        String label=(settings.getImageProvider()+"-"+settings.getImageModel()).replaceAll("[^a-zA-Z0-9_-]","_");
        Path cached=Path.of("../.local/marketing-live-"+label+".result");
        Path submitted=Path.of(cached+".submitted");
        boolean reused=Files.exists(cached);
        String url;
        if(reused) url=Files.readString(cached);
        else {
            assertThat(Files.exists(submitted)).as("此前请求结果待核实，不自动重复提交").isFalse();
            Files.writeString(submitted,"submitted",StandardOpenOption.CREATE_NEW);
            url=client.generate("为电影《熊出没·重启未来》制作宣传海报。营销标题：会员日相约，看熊大熊二闯未来。影片内容：熊大熊二与光头强进入未来世界，携手守护森林。","1:1");
            Files.writeString(cached,url,StandardOpenOption.CREATE_NEW);
        }
        var assets=new MarketingAssets(settings);
        String background=assets.saveBackground(client.download(url));
        var poster=assets.generatedPoster(background);
        assertThat(ImageIO.read(assets.path(poster.key()).toFile()).getWidth()).isGreaterThan(64);
        System.out.println("IMAGE_LIVE_OK model="+settings.getImageModel()+" poster="+assets.path(poster.key()).toAbsolutePath().normalize());
    }
}
