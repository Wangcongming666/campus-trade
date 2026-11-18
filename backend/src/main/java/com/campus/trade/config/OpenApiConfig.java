package com.campus.trade.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static {
        // 包装类型 Long（雪花主键）在 JSON 中序列化为字符串，文档同步标注为 string；基本类型 long（分页计数等）不受影响
        SpringDocUtils.getConfig().replaceWithSchema(Long.class, new StringSchema().format("int64").example("1858000000000000001"));
    }

    @Bean
    public OpenAPI campusOpenApi() {
        return new OpenAPI()
                .info(new Info().title("校园二手交易智能平台 API").version("v1.0")
                        .description("统一前缀 /api/v1，返回 {code,msg,data}，code=200 成功"))
                .components(new Components().addSecuritySchemes("Bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}
