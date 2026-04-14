package dev.iot.yandexservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yandex")
public class YandexProperties {

    private String oauthToken;
    private String baseUrl;
    private String groupActionPath;
    private String chandelierId;

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
}
