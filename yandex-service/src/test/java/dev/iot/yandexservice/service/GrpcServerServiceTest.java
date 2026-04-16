package dev.iot.yandexservice.service;

import dev.iot.yandexservice.config.YandexProperties;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yandex.Yandex;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrpcServerServiceTest {

    private static final String CHANDELIER_ID = "chandelier-123";

    @Mock
    private YandexRestClient yandexRestClient;

    @Mock
    private YandexProperties yandexProperties;

    @Mock
    private StreamObserver<Yandex.TurnOnOffLampResponse> responseObserver;

    private GrpcServerService grpcServerService;

    @BeforeEach
    void setUp() {
        grpcServerService = new GrpcServerService(yandexRestClient, yandexProperties);
        when(yandexProperties.getChandelierId()).thenReturn(CHANDELIER_ID);
    }

    @Test
    @DisplayName("Should turn on lamp successfully")
    void shouldTurnOnLampSuccessfully() {
        Yandex.TurnOnOffLampRequest request = Yandex.TurnOnOffLampRequest.newBuilder()
                .setTurnOn(true)
                .build();

        DeviceGroupActionResponse response = new DeviceGroupActionResponse("id", "ok", List.of());
        when(yandexRestClient.sendGroupAction(any(DeviceGroupActionRequest.class), eq(CHANDELIER_ID)))
                .thenReturn(response);

        grpcServerService.turnOnOffLamp(request, responseObserver);

        verify(responseObserver).onNext(argThat(resp ->
                resp.getStatus().equals("OK")
        ));

        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
    }

    @Test
    @DisplayName("Should turn off lamp successfully")
    void shouldTurnOffLampSuccessfully() {
        Yandex.TurnOnOffLampRequest request = Yandex.TurnOnOffLampRequest.newBuilder()
                .setTurnOn(true)
                .build();

        DeviceGroupActionResponse response = new DeviceGroupActionResponse("id", "ok", List.of());
        when(yandexRestClient.sendGroupAction(any(DeviceGroupActionRequest.class), eq(CHANDELIER_ID)))
                .thenReturn(response);

        grpcServerService.turnOnOffLamp(request, responseObserver);

        verify(responseObserver).onNext(argThat(resp ->
                resp.getStatus().equals("OK")
        ));

        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
    }

    @Test
    @DisplayName("Should handle API error response")
    void shouldHandleApiErrorResponse() {
        Yandex.TurnOnOffLampRequest request = Yandex.TurnOnOffLampRequest.newBuilder()
                .setTurnOn(true)
                .build();

        DeviceGroupActionResponse response = new DeviceGroupActionResponse("id", "error", List.of());
        when(yandexRestClient.sendGroupAction(any(DeviceGroupActionRequest.class), eq(CHANDELIER_ID)))
                .thenReturn(response);

        grpcServerService.turnOnOffLamp(request, responseObserver);

        verify(responseObserver).onError(any(RuntimeException.class));
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }
}

