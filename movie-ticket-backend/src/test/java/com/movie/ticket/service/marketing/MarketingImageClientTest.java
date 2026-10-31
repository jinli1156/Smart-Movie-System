package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class MarketingImageClientTest {
    @TempDir Path storage;
    final ObjectMapper json=new ObjectMapper();
    HttpServer server;
    MarketingSettings settings;
    MarketingImageClient client;
    final AtomicInteger requests=new AtomicInteger();
    int responseStatus=200;
    String responseBody;
    Map<String,Object> requestBody;
    String authorization,userAgent;

    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/images/generations",exchange->{
            requests.incrementAndGet();
            authorization=exchange.getRequestHeaders().getFirst("Authorization");
            userAgent=exchange.getRequestHeaders().getFirst("User-Agent");
            requestBody=json.readValue(exchange.getRequestBody(),Map.class);
            byte[] body=responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(responseStatus,body.length);
            try(var output=exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/v1/services/aigc/multimodal-generation/generation",exchange->{
            requests.incrementAndGet(); authorization=exchange.getRequestHeaders().getFirst("Authorization");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/json");
            requestBody=json.readValue(exchange.getRequestBody(),Map.class);
            byte[] body=responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(responseStatus,body.length);
            try(var output=exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        settings=new MarketingSettings(); settings.setImageProvider("openai-images");
        settings.setImageApiKey("image-test-key"); settings.setImageModel("gpt-image-2");
        settings.setImageBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/");
        settings.setProxyUrl(""); settings.setStoragePath(storage.toString());
        client=new MarketingImageClient(settings,json);
    }
    @AfterEach void close() { server.stop(0); }

    @Test void openAiImageEndpointCachesBase64BeforeReturningAndReusesItForDownloads() throws Exception {
        byte[] bytes=new byte[900_000]; Arrays.fill(bytes,(byte)7);
        responseBody=json.writeValueAsString(Map.of("data",List.of(Map.of("b64_json",Base64.getEncoder().encodeToString(bytes)))));
        String result=client.generate("电影宣传海报","3:4");
        assertThat(requestBody).containsEntry("model","gpt-image-2").containsEntry("prompt","电影宣传海报")
            .containsEntry("n",1).containsEntry("size","1152x1536");
        assertThat(authorization).isEqualTo("Bearer image-test-key");
        assertThat(userAgent).isNotBlank();
        assertThat(result).startsWith("local-image:").hasSize(48);
        assertThat(client.download(result)).isEqualTo(bytes);
        assertThat(client.download(result)).isEqualTo(bytes);
        assertThat(requests.get()).isEqualTo(1);
        client.releaseResult(result);
        assertThatThrownBy(()->client.download(result)).isInstanceOf(java.io.IOException.class);
    }
    @Test void openAiCanReturnADataUrlWithoutPuttingBase64InTheTaskRecord() throws Exception {
        responseBody=json.writeValueAsString(Map.of("data",List.of(Map.of("url","data:image/png;base64,AQIDBA=="))));
        String result=client.generate("海报","16:9");
        assertThat(requestBody).containsEntry("size","1536x864");
        assertThat(result).startsWith("local-image:").doesNotContain("base64");
        assertThat(client.download(result)).isEqualTo(new byte[]{1,2,3,4});
    }
    @Test void remoteUrlsStillPassThroughAndRequireAnAllowedImageHost() throws Exception {
        responseBody="{\"data\":[{\"url\":\"https://cdn.hangzhale.com/image.png\"}]}";
        assertThat(client.generate("海报","1:1")).isEqualTo("https://cdn.hangzhale.com/image.png");
        client.validateImageUrl("https://cdn.hangzhale.com/image.png");
        client.validateImageUrl("https://sfile.chatglm.cn/image.png");
        for(String unsafe:List.of("http://cdn.hangzhale.com/image.png","https://127.0.0.1/image.png",
            "https://hangzhale.com.evil.example/image.png","https://user@cdn.hangzhale.com/image.png"))
            assertThatThrownBy(()->client.validateImageUrl(unsafe)).isInstanceOf(MarketingImageClient.GenerationException.class);
    }
    @Test void legacyZhipuConfigurationKeepsItsEndpointKeyAndSize() throws Exception {
        settings.setImageProvider("zhipu"); settings.setImageApiKey(""); settings.setZhipuApiKey("legacy-test-key"); settings.setImageModel("glm-image");
        responseBody="{\"data\":[{\"url\":\"https://sfile.chatglm.cn/image.png\"}]}";
        client.generate("海报","3:4");
        assertThat(requestBody).containsEntry("size","1088x1472").containsEntry("model","glm-image").doesNotContainKey("n");
        assertThat(authorization).isEqualTo("Bearer legacy-test-key");
    }
    @Test void providerErrorsAreNotAutomaticallyRetriedAndServerErrorsRemainUncertain() {
        responseBody="{\"error\":{\"message\":\"provider error\"}}";
        for(int status:List.of(401,403,404,429,500)) {
            responseStatus=status;
            assertThatThrownBy(()->client.generate("海报","1:1")).isInstanceOfSatisfying(MarketingImageClient.GenerationException.class,error->{
                assertThat(error.code).isEqualTo("HTTP_"+status);
                assertThat(error.uncertain).isEqualTo(status>=500);
                assertThat(error.getMessage()).doesNotContain("image-test-key");
            });
        }
        assertThat(requests.get()).isEqualTo(5);
    }
    @Test void portraitUsesMultipartImageEditsAndPreservesTheReturnedResult() throws Exception {
        var received=new java.util.concurrent.atomic.AtomicReference<byte[]>();
        var contentType=new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/v1/images/edits",exchange->{
            requests.incrementAndGet(); received.set(exchange.getRequestBody().readAllBytes());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] body="{\"data\":[{\"b64_json\":\"AQIDBA==\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);
            try(var output=exchange.getResponseBody()) { output.write(body); }
        });
        assertThat(client.supportsReferencePhotos()).isTrue();
        String result=client.generateWithReference("我是主角，赛博朋克哪吒海报","3:4",new byte[]{1,2,3,4});
        String multipart=new String(received.get(),StandardCharsets.UTF_8);
        assertThat(contentType.get()).startsWith("multipart/form-data; boundary=Poster");
        assertThat(multipart).contains("name=\"image\"; filename=\"reference.jpg\"","Content-Type: image/jpeg","gpt-image-2","1152x1536","我是主角");
        assertThat(client.download(result)).isEqualTo(new byte[]{1,2,3,4});
        assertThat(requests.get()).isEqualTo(1);
        settings.setImageProvider("zhipu");
        assertThat(client.supportsReferencePhotos()).isFalse();
        assertThatThrownBy(()->client.generateWithReference("主角","3:4",new byte[]{1})).isInstanceOf(MarketingImageClient.GenerationException.class);
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void qwenImage3SendsReferenceAsBase64ToNativeMultimodalGeneration() throws Exception {
        settings.setImageProvider("qwen-images"); settings.setImageModel("qwen-image-3.0");
        responseBody="{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://dashscope-result-sz.oss-cn-shenzhen.aliyuncs.com/poster.png?Expires=123\"}]}}]}}";
        assertThat(settings.imageConfigured()).isTrue(); assertThat(client.supportsReferencePhotos()).isTrue();
        String result=client.generateWithReference("哪吒赛博朋克，保留照片人物面部特征","3:4",new byte[]{1,2,3,4});
        var payload=json.valueToTree(requestBody);
        assertThat(payload.path("model").asText()).isEqualTo("qwen-image-3.0");
        assertThat(payload.path("parameters").path("n").asInt()).isEqualTo(1);
        assertThat(payload.path("parameters").path("size").asText()).isEqualTo("1152*1536");
        var content=payload.path("input").path("messages").path(0).path("content");
        assertThat(content.path(0).path("image").asText()).isEqualTo("data:image/jpeg;base64,AQIDBA==");
        assertThat(content.path(1).path("text").asText()).contains("保留照片人物面部特征");
        assertThat(result).contains("dashscope-result-sz"); client.validateImageUrl(result);
        assertThat(authorization).isEqualTo("Bearer image-test-key"); assertThat(requests.get()).isEqualTo(1);
    }
    @Test void qwenTextOnlyUsesTheSameModelWithoutAnEmptyReference() throws Exception {
        settings.setImageProvider("qwen-images"); settings.setImageModel("qwen-image-3.0");
        responseBody="{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://dashscope-a717.oss-accelerate.aliyuncs.com/poster.png\"}]}}]}}";
        client.generate("科幻电影海报","16:9");
        var payload=json.valueToTree(requestBody);
        assertThat(payload.path("parameters").path("size").asText()).isEqualTo("1536*864");
        var content=payload.path("input").path("messages").path(0).path("content");
        assertThat(content.size()).isEqualTo(1); assertThat(content.path(0).has("image")).isFalse();
        assertThat(content.path(0).path("text").asText()).isEqualTo("科幻电影海报");
        client.validateImageUrl("https://dashscope-a717.oss-accelerate.aliyuncs.com/poster.png");
        for(String unsafe:List.of("https://evil.oss-cn-shanghai.aliyuncs.com/poster.png",
            "https://dashscope-result-sz.oss-cn-shenzhen.aliyuncs.com.evil.example/poster.png",
            "http://dashscope-a717.oss-accelerate.aliyuncs.com/poster.png"))
            assertThatThrownBy(()->client.validateImageUrl(unsafe)).isInstanceOf(MarketingImageClient.GenerationException.class);
        settings.setImageModel("qwen-image-plus"); assertThat(client.supportsReferencePhotos()).isFalse();
    }
    @Test void imageCredentialsDoNotEnableTheIndependentZhipuTextProvider() {
        assertThat(settings.imageConfigured()).isTrue();
        assertThat(settings.zhipuTextConfigured()).isFalse();
        assertThat(settings.getTextBaseUrl()).isEqualTo("https://open.bigmodel.cn/api/paas/v4");
        settings.setImageApiKey(""); settings.setZhipuApiKey("separate-text-key");
        assertThat(settings.zhipuTextConfigured()).isTrue();
        assertThat(settings.imageConfigured()).isFalse();
    }
}
