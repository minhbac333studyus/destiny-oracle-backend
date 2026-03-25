package com.destinyoracle.config;

import com.destinyoracle.service.ImageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects the active ImageProvider implementation based on app.image-provider config.
 *
 * Each provider is a @Component("name") — this config looks up the right one by name.
 * To add a new provider: just create a class implementing ImageProvider with @Component("newname"),
 * then set app.image-provider=newname in application.yml.
 */
@Slf4j
@Configuration
public class ImageProviderConfig {

    @Bean
    @Primary
    public ImageProvider activeImageProvider(
            @Value("${app.image-provider:gemini}") String providerName,
            ApplicationContext context) {

        try {
            ImageProvider provider = context.getBean(providerName, ImageProvider.class);
            log.info("━━━ Image provider: {} (bean: '{}') ━━━", provider.providerName(), providerName);
            return provider;
        } catch (Exception e) {
            log.warn("━━━ Image provider '{}' not found, falling back to gemini ━━━", providerName);
            return context.getBean("gemini", ImageProvider.class);
        }
    }
}
