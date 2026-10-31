package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.service.DeepSeekClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MARKETING_COPY_LIVE_TEST",matches="1")
class MarketingCopyLiveTest {
    @Test void realConfiguredTextServiceReturnsValidatedMarketingCopy() throws Exception {
        var properties=new Properties();
        try(var reader=Files.newBufferedReader(Path.of("../.local/marketing.properties"))) { properties.load(reader); }
        var settings=new MarketingSettings();
        settings.setZhipuApiKey(properties.getProperty("marketing.zhipu-api-key"));
        settings.setProxyUrl(properties.getProperty("marketing.proxy-url","http://127.0.0.1:7890"));
        var json=new ObjectMapper();
        var assistant=new Properties();
        Path assistantFile=Path.of("../.local/assistant.properties");
        if(Files.exists(assistantFile)) try(var reader=Files.newBufferedReader(assistantFile)) { assistant.load(reader); }
        var env=new StandardEnvironment();
        var deepSeek=new DeepSeekClient(json);
        ReflectionTestUtils.setField(deepSeek,"apiKey",env.resolvePlaceholders(assistant.getProperty("assistant.api-key","${DEEPSEEK_API_KEY:}")));
        ReflectionTestUtils.setField(deepSeek,"baseUrl",assistant.getProperty("assistant.base-url","https://api.deepseek.com"));
        ReflectionTestUtils.setField(deepSeek,"model",assistant.getProperty("assistant.model","deepseek-v4-flash"));
        ReflectionTestUtils.setField(deepSeek,"connectTimeoutMs",5000);
        ReflectionTestUtils.setField(deepSeek,"readTimeoutMs",60000);
        ReflectionTestUtils.setField(deepSeek,"proxyUrl",settings.getProxyUrl());
        var client=new MarketingTextClient(settings,deepSeek,new MarketingImageClient(settings,json),json);
        if(deepSeek.configured()) assertThat(client.model()).isEqualTo(deepSeek.model());
        assertThat(client.configured()).isTrue();
        var result=client.completeJson("你是影院营销文案编辑，只依据输入资料写作，不添加票价、优惠、评分或排名。返回 JSON：{\"movieId\":1,\"titles\":[\"标题一\",\"标题二\",\"标题三\"],\"shortCopy\":\"短文案\"}。movieId必须与输入一致，三个标题不同且各不超过60字，短文案不超过300字。",
            Map.of("movie",Map.of("id",1,"title","银幕上的旅途","genre","冒险","description","一段关于友情与勇气的温暖旅途"),"brief","周末朋友观影","audience","结伴观影的朋友"));
        MarketingWorker.validateCopy(result,1L);
        Files.writeString(Path.of("../.local/marketing-copy-live.json"),result.toPrettyString());
        System.out.println("MARKETING_COPY_LIVE_OK model="+client.model()+" schema=validated");
    }
}
