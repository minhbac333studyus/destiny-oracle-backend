package com.destinyoracle.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI destinyOracleOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Destiny Oracle API")
                        .description("REST API for the Destiny Oracle motivation card app")
                        .version("v1.0.0"));
    }

    /** Adds the X-User-Id header to every operation in Swagger UI */
    @Bean
    public OperationCustomizer addUserIdHeader() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(
                    new Parameter()
                            .in(ParameterIn.HEADER.toString())
                            .name("X-User-Id")
                            .description("User UUID (defaults to demo user if absent)")
                            .required(false)
                            .schema(new StringSchema()
                                    .example("00000000-0000-0000-0000-000000000001"))
            );
            return operation;
        };
    }
}
