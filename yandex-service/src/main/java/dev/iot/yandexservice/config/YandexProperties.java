package dev.iot.yandexservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "yandex")
public record YandexProperties(
        String oauthToken,
        String baseUrl,
        String groupActionPath,
        String chandelierId,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout
) {
}