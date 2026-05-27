package dev.iot.yandexservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "yandex")
public class YandexProperties {

    private String oauthToken;
    private String baseUrl;
    private String groupActionPath;
    private String chandelierId;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getOauthToken() {
        return oauthToken;
    }

    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getGroupActionPath() {
        return groupActionPath;
    }

    public void setGroupActionPath(String groupActionPath) {
        this.groupActionPath = groupActionPath;
    }

    public String getChandelierId() {
        return chandelierId;
    }

    public void setChandelierId(String chandelierId) {
        this.chandelierId = chandelierId;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}