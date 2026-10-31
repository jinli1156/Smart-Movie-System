package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.nio.file.*;
import java.util.*;

@Component
public class MarketingImageClient {
    private static final Logger log=LoggerFactory.getLogger(MarketingImageClient.class);
    private static final String INLINE_PREFIX="local-image:";
    private static final int MAX_IMAGE_BYTES=20*1024*1024;
    private static final String USER_AGENT="Mozilla/5.0 MovieTicketSystem/1.0";
    private final MarketingSettings settings;
    private final ObjectMapper json;
    public MarketingImageClient(MarketingSettings settings,ObjectMapper json) { this.settings=settings; this.json=json; }
    public static class GenerationException extends IOException {
        final String code; final boolean uncertain;
        GenerationException(String code,String message,boolean uncertain) { super(message); this.code=code; this.uncertain=uncertain; }
        public boolean isUncertain() { return uncertain; }
    }
    HttpClient client() {
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(settings.getConnectTimeoutSeconds())).followRedirects(HttpClient.Redirect.NEVER);
        String proxy=settings.getProxyUrl();
        if(proxy!=null&&!proxy.isBlank()) {
            URI uri=URI.create(proxy);
            builder.proxy(new ProxySelector() {
                public List<Proxy> select(URI target) {
                    if(Set.of("localhost","127.0.0.1","::1").contains(target.getHost())) return List.of(Proxy.NO_PROXY);
                    return List.of(new Proxy(Proxy.Type.HTTP,new InetSocketAddress(uri.getHost(),uri.getPort())));
                }
                public void connectFailed(URI uri,SocketAddress address,IOException error) {}
            });
        }
        return builder.build();
    }
    public String generate(String prompt,String ratio) throws IOException,InterruptedException {
        if(!settings.imageConfigured()) throw new GenerationException("NOT_CONFIGURED","请先配置生图服务",false);
        if("qwen-images".equals(settings.getImageProvider())) return generateQwen(prompt,ratio,null);
        boolean openai=settings.getImageProvider().equals("openai-images");
        String size=openai?switch(ratio) { case "16:9" -> "1536x864"; case "3:4" -> "1152x1536"; default -> "1024x1024"; }:
            switch(ratio) { case "16:9" -> "1728x960"; case "3:4" -> "1088x1472"; default -> "1280x1280"; };
        if(!openai&&!settings.getImageModel().equals("glm-image")) size=switch(ratio) { case "16:9" -> "1344x768"; case "3:4" -> "864x1152"; default -> "1024x1024"; };
        var body=new LinkedHashMap<String,Object>();
        body.put("model",settings.getImageModel()); body.put("prompt",prompt); body.put("size",size);
        if(openai) body.put("n",1);
        HttpRequest request=HttpRequest.newBuilder(URI.create(settings.getImageBaseUrl().replaceAll("/+$","")+"/images/generations"))
            .timeout(Duration.ofSeconds(settings.getRequestTimeoutSeconds()))
            .header("Authorization","Bearer "+settings.effectiveImageApiKey()).header("Content-Type","application/json")
            .header("User-Agent",USER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return submit(request,openai);
    }
    public boolean supportsReferencePhotos() {
        return settings.imageConfigured() && ("openai-images".equals(settings.getImageProvider())
            || "qwen-images".equals(settings.getImageProvider()) && Set.of("qwen-image-3.0","qwen-image-3.0-pro",
                "qwen-image-2.1-pro","qwen-image-2.1-turbo").contains(settings.getImageModel()));
    }
    /** Reference bytes are normalized JPEGs from private storage, never public URLs. */
    public String generateWithReference(String prompt,String ratio,byte[] reference) throws IOException,InterruptedException {
        if(!supportsReferencePhotos()) throw new GenerationException("NOT_SUPPORTED","当前生图服务暂不支持人物照片，请移除照片后生成",false);
        if("qwen-images".equals(settings.getImageProvider())) return generateQwen(prompt,ratio,reference);
        String boundary="Poster"+UUID.randomUUID().toString().replace("-","");
        var body=new ByteArrayOutputStream();
        String size=switch(ratio) { case "16:9" -> "1536x864"; case "3:4" -> "1152x1536"; default -> "1024x1024"; };
        for(var entry:Map.of("model",settings.getImageModel(),"prompt",prompt,"size",size,"n","1").entrySet()) {
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"image\"; filename=\"reference.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        body.write(reference); body.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create(settings.getImageBaseUrl().replaceAll("/+$","")+"/images/edits"))
            .timeout(Duration.ofSeconds(settings.getRequestTimeoutSeconds()))
            .header("Authorization","Bearer "+settings.effectiveImageApiKey()).header("Content-Type","multipart/form-data; boundary="+boundary)
            .header("User-Agent",USER_AGENT).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return submit(request,true);
    }
    private String generateQwen(String prompt,String ratio,byte[] reference) throws IOException,InterruptedException {
        String size=switch(ratio) { case "16:9" -> "1536*864"; case "3:4" -> "1152*1536"; default -> "1024*1024"; };
        var content=new ArrayList<Map<String,String>>();
        if(reference!=null) content.add(Map.of("image","data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(reference)));
        content.add(Map.of("text",prompt));
        var payload=Map.of("model",settings.getImageModel(),"input",Map.of("messages",List.of(Map.of("role","user","content",content))),
            "parameters",Map.of("size",size,"n",1));
        var request=HttpRequest.newBuilder(URI.create(settings.getImageBaseUrl().replaceAll("/+$","")+"/services/aigc/multimodal-generation/generation"))
            .timeout(Duration.ofSeconds(settings.getRequestTimeoutSeconds()))
            .header("Authorization","Bearer "+settings.effectiveImageApiKey()).header("Content-Type","application/json")
            .header("User-Agent",USER_AGENT).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
        return submit(request,false,true);
    }
    private String submit(HttpRequest request,boolean openai) throws IOException,InterruptedException {
        return submit(request,openai,false);
    }
    private String submit(HttpRequest request,boolean openai,boolean qwen) throws IOException,InterruptedException {
        try {
            var response=client().send(request,HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            int limit=openai?32*1024*1024:1_000_000;
            try(var input=response.body()) { bytes=input.readNBytes(limit+1); }
            if(bytes.length>limit) throw new GenerationException("INVALID_RESPONSE","生图响应过大，请核实任务结果",true);
            int status=response.statusCode();
            if(status!=200) throw new GenerationException("HTTP_"+status,
                status==401||status==403?"生图服务鉴权失败，请检查配置":
                status==429?"生图请求受限，请稍后重试":"生图请求失败（HTTP "+status+"）",status>=500||status==408);
            var root=json.readTree(bytes);
            var result=qwen?root.path("output").path("choices").path(0).path("message"):root.path("data").path(0);
            String encoded=result.path("b64_json").asText();
            if(!encoded.isBlank()) return persistInline(encoded);
            var qwenImage=qwen?result.path("content").findValue("image"):null;
            String url=qwen?(qwenImage==null?"":qwenImage.asText()):result.path("url").asText();
            if(url.startsWith("data:image/")) {
                int comma=url.indexOf(',');
                if(comma<0||!url.substring(0,comma).endsWith(";base64")) throw new GenerationException("INVALID_IMAGE","图片编码无效，请核实结果",true);
                return persistInline(url.substring(comma+1));
            }
            if(url.isBlank()) throw new GenerationException("EMPTY_IMAGE","生图服务未返回图片，请核实任务结果",true);
            // Persist the provider result before downloading. A CDN/storage issue must not trigger another paid call.
            return url;
        } catch(GenerationException e) { throw e; }
        catch(HttpTimeoutException|ConnectException e) { throw new GenerationException("SUBMIT_UNKNOWN","请求结果尚未确定，请核实后再决定是否重新生成",true); }
        catch(IOException e) {
            String reason=Objects.toString(e.getMessage(),"");
            String key=settings.effectiveImageApiKey();
            if(key!=null&&!key.isBlank()) reason=reason.replace(key,"[REDACTED]");
            log.warn("生图连接中断 provider={} model={} errorType={} reason={}",settings.getImageProvider(),settings.getImageModel(),e.getClass().getSimpleName(),reason);
            throw new GenerationException("SUBMIT_UNKNOWN","生图连接中断，请核实后再决定是否重新生成",true);
        }
    }
    private String persistInline(String value) throws IOException {
        if(value.length()>((MAX_IMAGE_BYTES+2)/3)*4) throw new GenerationException("INVALID_IMAGE","图片超出大小限制，请核实结果",true);
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(value); }
        catch(IllegalArgumentException e) { throw new GenerationException("INVALID_IMAGE","图片编码无效，请核实结果",true); }
        if(bytes.length==0||bytes.length>MAX_IMAGE_BYTES) throw new GenerationException("INVALID_IMAGE","图片大小无效，请核实结果",true);
        String token=UUID.randomUUID().toString();
        Path file=inlinePath(INLINE_PREFIX+token);
        Files.createDirectories(file.getParent());
        Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
        return INLINE_PREFIX+token;
    }
    private Path inlinePath(String result) throws IOException {
        String token=result.substring(INLINE_PREFIX.length());
        if(!token.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) throw new IOException("图片缓存标识无效");
        return Path.of(settings.getStoragePath()).toAbsolutePath().normalize().resolve(".provider-results").resolve(token+".bin");
    }
    public void releaseResult(String result) {
        if(!result.startsWith(INLINE_PREFIX)) return;
        try { Files.deleteIfExists(inlinePath(result)); }
        catch(IOException e) { log.warn("生图结果已保存，临时缓存清理失败"); }
    }
    void validateImageUrl(String url) throws GenerationException {
        URI uri;
        try { uri=URI.create(url); } catch(Exception e) { throw new GenerationException("INVALID_IMAGE_URL","生图结果地址无效",true); }
        String host=Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT);
        boolean allowed=host.matches("dashscope-[a-z0-9-]+\\.oss-[a-z0-9-]+\\.aliyuncs\\.com")
            ||host.equals("maas-watermark-prod-new.cn-wlcb.ufileos.com")
            ||List.of("bigmodel.cn","chatglm.cn","zhipuai.cn").stream().anyMatch(d->host.equals(d)||host.endsWith("."+d));
        if(!allowed&&settings.getImageDownloadHosts()!=null) allowed=settings.getImageDownloadHosts().stream()
            .filter(Objects::nonNull).map(d->d.trim().toLowerCase(Locale.ROOT)).filter(d->!d.isBlank())
            .anyMatch(d->host.equals(d)||host.endsWith("."+d));
        if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||!allowed||(uri.getPort()!=-1&&uri.getPort()!=443))
            throw new GenerationException("INVALID_IMAGE_URL","生图结果地址不在允许的素材域名内（"+host+"）",true);
    }
    public byte[] download(String url) throws IOException,InterruptedException {
        if(url.startsWith(INLINE_PREFIX)) {
            Path file=inlinePath(url);
            long size=Files.size(file);
            if(size==0||size>MAX_IMAGE_BYTES) throw new IOException("图片缓存大小无效");
            return Files.readAllBytes(file);
        }
        validateImageUrl(url);
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(settings.getDownloadTimeoutSeconds())).header("User-Agent",USER_AGENT).GET().build();
        var response=client().send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            if(response.statusCode()!=200) throw new IOException("图片转存失败");
            byte[] bytes=input.readNBytes(20*1024*1024+1);
            if(bytes.length>20*1024*1024) throw new IOException("图片超出大小限制");
            return bytes;
        }
    }
}
