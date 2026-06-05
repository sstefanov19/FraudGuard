package com.fraudguard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI fraudGuardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FraudGuard Payments API")
                        .version("0.1.0")
                        .description("Real-time, explainable fraud detection for payment authorizations.")
                        .contact(new Contact().name("FraudGuard")))
                .addServersItem(new Server()
                        .url("/")
                        .description("Current deployment"))
                .addTagsItem(new Tag()
                        .name("Transactions")
                        .description("Payment authorization scoring and idempotent fraud decisions"));
    }
}
