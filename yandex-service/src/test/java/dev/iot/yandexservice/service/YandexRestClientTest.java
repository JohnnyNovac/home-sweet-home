package dev.iot.yandexservice.service;

import dev.iot.yandexservice.config.YandexProperties;
import dev.iot.yandexservice.dto.Capability;
import dev.iot.yandexservice.dto.CapabilityState;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YandexRestClientTest {

    private static final String GROUP_ACTION_PATH = "/iot/operations/v1.0/devices/groups/{groupId}/actions";
    private static final String CHANDELIER_ID = "chandelier-123";

    @Mock
    private RestClient restClient;

    @Mock
    private YandexProperties yandexProperties;

    private YandexRestClient yandexRestClient;

    @BeforeEach
    void setUp() {
        yandexRestClient = new YandexRestClient(restClient, yandexProperties);
        when(yandexProperties.getGroupActionPath()).thenReturn(GROUP_ACTION_PATH);
    }

    @Test
    @DisplayName("Should send group action request successfully")
    void shouldSendGroupActionSuccessfully() {
        DeviceGroupActionRequest request = new DeviceGroupActionRequest(
                List.of(
                        new Capability(
                                "devices.capabilities.on_off",
                                new CapabilityState("on", true)
                        )
                )
        );

        DeviceGroupActionResponse expectedResponse = new DeviceGroupActionResponse("ok", null, null);

        RequestBodyUriSpec requestBodyUriSpec = mock(RequestBodyUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(GROUP_ACTION_PATH, CHANDELIER_ID)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(DeviceGroupActionResponse.class)).thenReturn(expectedResponse);

        DeviceGroupActionResponse result = yandexRestClient.sendGroupAction(request, CHANDELIER_ID);

        assertThat(result).isEqualTo(expectedResponse);
        verify(restClient).post();
    }
}

