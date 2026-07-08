package dev.iot.presenceservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EventServiceClientConfig {

    private final EventServiceProperties eventServiceProperties;

    public EventServiceClientConfig(EventServiceProperties eventServiceProperties) {
        this.eventServiceProperties = eventServiceProperties;
    }

    @Bean
    public RestClient eventServiceRestClient() {
        return RestClient.builder()
                .baseUrl(eventServiceProperties.url())
                .build();
    }
}
