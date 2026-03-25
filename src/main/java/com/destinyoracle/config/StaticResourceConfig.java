package com.destinyoracle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves generated images from the filesystem at runtime.
 *
 * Spring Boot's default static resource handler only sees files that existed at startup.
 * This handler maps /generated/** to the actual filesystem directory so newly created
 * images (jpg, png, webp) are immediately accessible without a restart.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String generatedPath = "file:" + System.getProperty("user.dir")
                + "/src/main/resources/static/generated/";

        registry.addResourceHandler("/generated/**")
                .addResourceLocations(generatedPath)
                .setCachePeriod(0); // no cache during dev — always serve fresh
    }
}
