package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.service.DeepSeekClient;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Marketing-only text routing. Existing assistant configuration remains independent. */
@Component
public class MarketingTextClient {
    public static class TextException extends IOException {
        final String code;
        TextException(String code,String message) { super(message); this.code=code; }
    }
    private final MarketingSettings settings;
    private final DeepSeekClient deepSeek;
    private final MarketingImageClient transport;
    private final ObjectMapper json;
    public MarketingTextClient(MarketingSettings settings,DeepSeekClient deepSeek,MarketingImageClient transport,ObjectMapper json) {
        this.settings=settings; this.deepSeek=deepSeek; this.transport=transport; this.json=json;
    }
    private boolean usesDeepSeek() { return settings.getTextProvider().equals("deepseek") || settings.getTextProvider().equals("auto")&&deepSeek.configured(); }
    public boolean configured() { return usesDeepSeek()?deepSeek.configured():settings.zhipuTextConfigured(); }
    public String model() { return usesDeepSeek()?deepSeek.model():settings.getTextModel(); }
    public JsonNode completeJson(String system,Object context) throws IOException,InterruptedException {
        if(usesDeepSeek()) return deepSeek.completeJson(system,context);
        if(!configured()) throw new IOException("文案服务未配置");
        var body=Map.of("model",settings.getTextModel(),
            "messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",json.writeValueAsString(context))),
            "response_format",Map.of("type","json_object"),"thinking",Map.of("type","disabled"),"max_tokens",1600);
        var request=HttpRequest.newBuilder(URI.create(settings.getTextBaseUrl()+"/chat/completions"))
            .timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+settings.getZhipuApiKey())
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response=transport.client().send(request,HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try(var input=response.body()) { bytes=input.readNBytes(1_000_001); }
        if(bytes.length>1_000_000) throw new IOException("文案响应过大");
        if(response.statusCode()!=200) {
            JsonNode error=json.readTree(bytes).path("error");
            String code=error.path("code").asText().replaceAll("[^a-zA-Z0-9_-]","");
            int status=response.statusCode();
            throw new TextException("HTTP_"+status+"_"+code,status==429?"文案服务暂时繁忙，请稍后手动重试":
                status==401||status==403?"文案服务鉴权失败，请检查配置":"文案模型请求失败（HTTP "+status+" / "+code+"）");
        }
        JsonNode choice=json.readTree(bytes).path("choices").path(0);
        if(!choice.path("finish_reason").asText().equals("stop")) throw new IOException("文案输出不完整");
        JsonNode result=json.readTree(choice.path("message").path("content").asText());
        if(result==null||!result.isObject()) throw new IOException("文案格式无效");
        return result;
    }
}
