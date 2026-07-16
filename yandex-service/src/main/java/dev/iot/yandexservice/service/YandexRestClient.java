package dev.iot.yandexservice.service;

import dev.iot.yandexservice.config.YandexProperties;
import dev.iot.yandexservice.dto.DeleteDeviceResponse;
import dev.iot.yandexservice.dto.DeviceActionRequest;
import dev.iot.yandexservice.dto.DeviceActionResponse;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
import dev.iot.yandexservice.dto.DeviceGroupResponse;
import dev.iot.yandexservice.dto.DeviceResponse;
import dev.iot.yandexservice.dto.ScenarioActionResponse;
import dev.iot.yandexservice.dto.UserInfoResponse;
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
    private final RestClient.ResponseSpec.ErrorHandler errorHandler =
            (request, response) -> {
                throw toApiException(response);
            };

    public YandexRestClient(RestClient client, YandexProperties yandexProperties, ObjectMapper objectMapper) {
        this.client = client;
        this.yandexProperties = yandexProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Reads the whole smart home of the user.
     *
     * @return the Yandex API response with the households, rooms, groups, devices and scenarios of the user
     * @throws YandexApiException if the Yandex API answers with a 4xx/5xx status
     */
    public UserInfoResponse getUserInfo() {
        return client.get()
                .uri(yandexProperties.userInfoPath())
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler)
                .body(UserInfoResponse.class);
    }

    /**
     * Reads a single device with its current state.
     *
     * @param deviceId ID of the device to read
     * @return the Yandex API response with the capabilities and properties of the device
     * @throws YandexApiException if the Yandex API answers with a 4xx/5xx status
     */
    public DeviceResponse getDeviceInfo(String deviceId) {
        return client.get()
                .uri(yandexProperties.devicePath(), deviceId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler)
                .body(DeviceResponse.class);
    }

    /**
     * Sends commands to one or more devices.
     *
     * @param request the request with actions per device
     * @return the Yandex API response with the results of the actions for every device
     * @throws YandexApiException if the Yandex API answers with a 4xx/5xx status
     */
    public DeviceActionResponse sendDeviceAction(DeviceActionRequest request) {
        return client.post()
                .uri(yandexProperties.deviceActionPath())
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler)
                .body(DeviceActionResponse.class);
    }

    /**
     * Reads a group of devices with its current state.
     *
     * @param groupId ID of the device group to read
     * @return the Yandex API response with the capabilities of the group and the devices it holds
     * @throws YandexApiException if the Yandex API answers with a 4xx/5xx status
     */
    public DeviceGroupResponse getGroupInfo(String groupId) {
        return client.get()
                .uri(yandexProperties.groupPath(), groupId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler)
                .body(DeviceGroupResponse.class);
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
                .onStatus(HttpStatusCode::isError, errorHandler)
                .body(DeviceGroupActionResponse.class);
    }

    /**
     * Runs a scenario.
     *
     * @param scenarioId ID of the scenario to run
     * @return the Yandex API response with the status of the request
     * @throws YandexApiException if the Yandex API answers with a 4xx/5xx status
     */
    public ScenarioActionResponse runScenario(String scenarioId) {
        return client.post()
                .uri(yandexProperties.scenarioActionPath(), scenarioId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler)
                .body(ScenarioActionResponse.class);
    }

    /**
     * Unlinks a device from the smart home of the user.
     *
     * @param deviceId ID of the device to delete
     * @return the Yandex API response with the status of the request
     * @throws YandexApiException if the Yandex API answers with a 4xx/5xx status
     */
    public DeleteDeviceResponse deleteDevice(String deviceId) {
        return client.delete()
                .uri(yandexProperties.devicePath(), deviceId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errorHandler)
                .body(DeleteDeviceResponse.class);
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