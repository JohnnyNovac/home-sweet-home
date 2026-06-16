package dev.iot.yandexservice.service;

import dev.iot.yandexservice.config.YandexProperties;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class YandexRestClient {

    private final RestClient client;
    private final YandexProperties yandexProperties;

    public YandexRestClient(RestClient client, YandexProperties yandexProperties) {
        this.client = client;
        this.yandexProperties = yandexProperties;
    }

    /**
     * Sends a command to a group of devices.
     *
     * @param request the request with actions for the group of devices
     * @param groupId ID of the device group to apply the actions to
     * @return the Yandex API response with the results of the actions for the group
     */
    public DeviceGroupActionResponse sendGroupAction(DeviceGroupActionRequest request, String groupId) {
        return client.post()
                .uri(yandexProperties.getGroupActionPath(), groupId)
                .body(request)
                .retrieve()
                .body(DeviceGroupActionResponse.class);
    }
}
