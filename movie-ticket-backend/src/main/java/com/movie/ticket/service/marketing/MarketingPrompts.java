package com.movie.ticket.service.marketing;

import com.fasterxml.jackson.databind.JsonNode;

final class MarketingPrompts {
    private MarketingPrompts() {}
    static final int IMAGE_PROMPT_VERSION = 3;

    static final String IMAGE_INSTRUCTION = """
        根据输入的电影资料 movie 和营销标题 marketingTitle，为该影片构思一张呼应营销标题的宣传海报，编写给生图模型的绘图提示词。
        返回 JSON：{"movieId":123,"prompt":"绘图提示词"}，movieId 与输入 movie.id 一致。
        """;

    static String imagePrompt(JsonNode movie,String marketingTitle,String prompt) {
        return prompt+"\n影片：《"+movie.path("title").asText()+"》；类型："+movie.path("genre").asText()
            +"；影片内容："+movie.path("description").asText()+"\n营销标题："+marketingTitle;
    }
}
