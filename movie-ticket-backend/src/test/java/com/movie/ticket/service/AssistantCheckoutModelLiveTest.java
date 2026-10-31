package com.movie.ticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.ticket.dto.AssistantChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Paid-model opt-in; inventory and proposal storage are synthetic, no business orders are created. */
@EnabledIfEnvironmentVariable(named="ASSISTANT_CHECKOUT_LIVE",matches="1")
class AssistantCheckoutModelLiveTest {
    @Test void selectedOptionAndPurchaseRequestProduceAConfirmationCardWithoutCreatingAnOrder() throws Exception {
        var json=new ObjectMapper(); var client=new DeepSeekClient(json); var props=new Properties();
        if(Files.exists(Path.of("../.local/assistant.properties"))) try(var reader=Files.newBufferedReader(Path.of("../.local/assistant.properties"))) { props.load(reader); }
        String key=System.getenv().getOrDefault("DEEPSEEK_API_KEY",props.getProperty("DEEPSEEK_API_KEY",""));
        assertFalse(key.isBlank()); ReflectionTestUtils.setField(client,"apiKey",key);
        ReflectionTestUtils.setField(client,"baseUrl",System.getenv().getOrDefault("AI_ASSISTANT_BASE_URL","https://api.deepseek.com"));
        ReflectionTestUtils.setField(client,"model",System.getenv().getOrDefault("AI_ASSISTANT_MODEL","deepseek-v4-flash"));
        ReflectionTestUtils.setField(client,"connectTimeoutMs",5000); ReflectionTestUtils.setField(client,"readTimeoutMs",60000);
        var tools=mock(AssistantToolService.class);
        try(var input=new ClassPathResource("assistant/tools.json").getInputStream()) { when(tools.definitions()).thenReturn(json.readTree(input)); }
        when(tools.execute(anyString(),anyString(),eq(7L))).thenAnswer(i -> {
            String name=i.getArgument(0); var criteria=json.readTree((String)i.getArgument(1));
            if(!name.equals("prepare_ticket_order")) return Map.of("ok",false,"message","当前已确定场次，请准备订单确认卡");
            assertEquals(202,criteria.path("showtimeId").asInt()); assertEquals(2,criteria.path("people").asInt());
            assertFalse(criteria.path("usePoints").asBoolean(true)); assertEquals(80,criteria.path("maxTotal").asInt());
            return Map.of("ok",true,"source",name,"data",Map.of("draftId","synthetic-only","movieTitle","合成测试影片2","showtimeId",202,"people",2,"seats",List.of("5排5座","5排6座"),"originalPrice",80,"bestPlan",Map.of("totalPrice",70,"pointsUsed",0,"couponDiscount",10),"notice","未锁座、未创建订单，用户确认后进入支付页"));
        });
        var request=new AssistantChatRequest(); request.setMessage("就要第二个方案，帮我下单两张，不用积分，支付我自己来。");
        request.setWatchPlan(json.readTree("""
          {"criteria":{"people":2,"maxTotal":80,"usePoints":false},"options":[
          {"kind":"showtime","id":101,"movieId":1,"title":"合成测试影片1"},
          {"kind":"showtime","id":202,"movieId":2,"title":"合成测试影片2"}]}
          """));
        var response=new MovieAssistantService(client,tools,json).converse(request,7L,(event,data)->{},()->false);
        Files.createDirectories(Path.of("../.local/assistant-upgrade"));
        Files.writeString(Path.of("../.local/assistant-upgrade/checkout-model-trace.json"),json.writeValueAsString(response));
        assertEquals("model",response.getSource(),response.getAnswer());
        assertTrue(response.getToolResults().stream().anyMatch(r->Boolean.TRUE.equals(r.get("ok"))&&"prepare_ticket_order".equals(r.get("source"))));
        assertTrue(response.getAnswer().contains("确认下单")); assertTrue(response.getAnswer().contains("支付由你"));
        verify(tools,never()).execute(eq("create_order"),anyString(),any());
        verify(tools,never()).execute(eq("pay_order"),anyString(),any());
    }
}
