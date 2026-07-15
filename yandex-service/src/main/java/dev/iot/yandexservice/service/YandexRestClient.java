package dev.iot.yandexservice.service;

import dev.iot.yandexservice.config.YandexProperties;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
import dev.iot.yandexservice.dto.YandexErrorResponse;
import dev.iot.yandexservice.exception.YandexApiException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Service
public class YandexRestClient {

    private final RestClient client;
    private final YandexProperties yandexProperties;
    private final ObjectMapper objectMapper;

    public YandexRestClient(RestClient client, YandexProperties yandexProperties, ObjectMapper objectMapper) {
        this.client = client;
        this.yandexProperties = yandexProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a command to a group of devices.
     *
     * @param request the request with actions for the group of devices
     * @param groupId ID of the device group to apply the actions to
     * @return the Yandex API response with the results of the actions for the group
     * @throws YandexApiException if the Yandex API answers with a 4xx/5xx status
     */
    public DeviceGroupActionResponse sendGroupAction(DeviceGroupActionRequest request, String groupId) {
        return client.post()
                .uri(yandexProperties.groupActionPath(), groupId)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw toApiException(res);
                })
                .body(DeviceGroupActionResponse.class);
    }

    private YandexApiException toApiException(ClientHttpResponse response) throws IOException {
        int statusCode = response.getStatusCode().value();
        YandexErrorResponse error = readError(response);

        if (error == null) {
            return new YandexApiException(statusCode, null, "Yandex API returned HTTP " + statusCode);
        }

        String message = error.message() != null
                ? error.message()
                : "Yandex API returned HTTP " + statusCode + " with status=" + error.status();

        return new YandexApiException(statusCode, error.requestId(), message);
    }

    /**
     * Reads the documented error body. Returns null when the body is absent or is not the expected JSON,
     * so that a gateway or proxy error page never masks the original HTTP status.
     */
    private YandexErrorResponse readError(ClientHttpResponse response) {
        try {
            return objectMapper.readValue(response.getBody(), YandexErrorResponse.class);
        } catch (Exception e) {
            return null;
        }
    }
}