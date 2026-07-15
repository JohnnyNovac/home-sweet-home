package dev.iot.yandexservice.service;

import dev.iot.yandexservice.config.YandexProperties;
import dev.iot.yandexservice.dto.Capability;
import dev.iot.yandexservice.dto.State;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
import dev.iot.yandexservice.exception.YandexApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.ResponseSpec;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

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
        yandexRestClient = new YandexRestClient(restClient, yandexProperties, snakeCaseMapper());
        when(yandexProperties.groupActionPath()).thenReturn(GROUP_ACTION_PATH);
    }

    /**
     * Mirrors spring.jackson.property-naming-strategy=SNAKE_CASE from application.yml.
     */
    private static ObjectMapper snakeCaseMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }

    private static DeviceGroupActionRequest turnOnRequest() {
        return new DeviceGroupActionRequest(
                List.of(
                        new Capability(
                                "devices.capabilities.on_off",
                                new State("on", true)
                        )
                )
        );
    }

    @Test
    @DisplayName("Should send group action request successfully")
    void shouldSendGroupActionSuccessfully() {
        DeviceGroupActionRequest request = turnOnRequest();

        DeviceGroupActionResponse expectedResponse = new DeviceGroupActionResponse(null, "ok", null);

        RequestBodyUriSpec requestBodyUriSpec = mock(RequestBodyUriSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(GROUP_ACTION_PATH, CHANDELIER_ID)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(any(DeviceGroupActionRequest.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.body(DeviceGroupActionResponse.class)).thenReturn(expectedResponse);

        DeviceGroupActionResponse result = yandexRestClient.sendGroupAction(request, CHANDELIER_ID);

        assertThat(result).isEqualTo(expectedResponse);
        verify(restClient).post();
    }

    @Test
    @DisplayName("Should translate a documented error body into YandexApiException")
    void shouldTranslateErrorBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YandexRestClient client = new YandexRestClient(builder.build(), yandexProperties, snakeCaseMapper());

        server.expect(requestTo("/iot/operations/v1.0/devices/groups/chandelier-123/actions"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"request_id\":\"req-42\",\"status\":\"error\",\"message\":\"Group not found\"}"));

        assertThatThrownBy(() -> client.sendGroupAction(turnOnRequest(), CHANDELIER_ID))
                .isInstanceOf(YandexApiException.class)
                .hasMessage("Group not found")
                .satisfies(thrown -> {
                    YandexApiException ex = (YandexApiException) thrown;
                    assertThat(ex.getStatusCode()).isEqualTo(404);
                    assertThat(ex.getRequestId()).isEqualTo("req-42");
                });

        server.verify();
    }

    @Test
    @DisplayName("Should keep the HTTP status when the error body is not the documented JSON")
    void shouldFallBackOnUnparsableErrorBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YandexRestClient client = new YandexRestClient(builder.build(), yandexProperties, snakeCaseMapper());

        server.expect(requestTo("/iot/operations/v1.0/devices/groups/chandelier-123/actions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>502 Bad Gateway</html>"));

        assertThatThrownBy(() -> client.sendGroupAction(turnOnRequest(), CHANDELIER_ID))
                .isInstanceOf(YandexApiException.class)
                .hasMessage("Yandex API returned HTTP 502")
                .satisfies(thrown -> assertThat(((YandexApiException) thrown).getStatusCode()).isEqualTo(502));

        server.verify();
    }
}