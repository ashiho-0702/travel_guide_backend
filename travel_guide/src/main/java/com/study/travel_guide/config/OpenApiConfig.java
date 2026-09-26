package com.study.travel_guide.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("AI 旅行规划小程序")
                .description("AI 旅行规划 + 语音导游后端接口")
                .version("1.0.0"));
    }
}
