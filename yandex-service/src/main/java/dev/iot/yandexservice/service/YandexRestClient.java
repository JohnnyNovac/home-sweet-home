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
     * Отправляет команду на группу устройств
     *
     * @param request запрос с действиями для группы устройств
     * @param groupId ID группы устройств, к которой нужно применить действия
     * @return ответ от API Яндекса с результатами выполнения действий для группы устройств
     */
    public DeviceGroupActionResponse sendGroupAction(DeviceGroupActionRequest request, String groupId) {
        return client.post()
                .uri(yandexProperties.getGroupActionPath(), groupId)
                .body(request)
                .retrieve()
                .body(DeviceGroupActionResponse.class);
    }
}
