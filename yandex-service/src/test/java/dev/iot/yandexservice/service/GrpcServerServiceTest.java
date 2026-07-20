package dev.iot.yandexservice.service;

import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yandex.Yandex;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrpcServerServiceTest {

    private static final String EXTERNAL_ID = "chandelier-123";

    @Mock
    private YandexRestClient yandexRestClient;

    @Mock
    private StreamObserver<Yandex.SetStateResponse> responseObserver;

    private GrpcServerService grpcServerService;

    @BeforeEach
    void setUp() {
        grpcServerService = new GrpcServerService(yandexRestClient);
    }

    @Test
    @DisplayName("Should turn the group lamp on and forward on=true to Yandex")
    void shouldTurnOnLampSuccessfully() {
        Yandex.SetStateRequest request = Yandex.SetStateRequest.newBuilder()
                .setExternalId(EXTERNAL_ID)
                .setOn(true)
                .setKind(Yandex.TargetKind.GROUP)
                .build();

        DeviceGroupActionResponse response = new DeviceGroupActionResponse("id", "ok", List.of());
        when(yandexRestClient.sendGroupAction(any(DeviceGroupActionRequest.class), eq(EXTERNAL_ID)))
                .thenReturn(response);

        grpcServerService.setState(request, responseObserver);

        ArgumentCaptor<DeviceGroupActionRequest> requestCaptor = ArgumentCaptor.forClass(DeviceGroupActionRequest.class);
        verify(yandexRestClient).sendGroupAction(requestCaptor.capture(), eq(EXTERNAL_ID));
        assertThat(requestCaptor.getValue().actions().getFirst().state().value()).isEqualTo(true);

        verify(responseObserver).onNext(any());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
    }

    @Test
    @DisplayName("Should turn the group lamp off and forward on=false to Yandex")
    void shouldTurnOffLampSuccessfully() {
        Yandex.SetStateRequest request = Yandex.SetStateRequest.newBuilder()
                .setExternalId(EXTERNAL_ID)
                .setOn(false)
                .setKind(Yandex.TargetKind.GROUP)
                .build();

        DeviceGroupActionResponse response = new DeviceGroupActionResponse("id", "ok", List.of());
        when(yandexRestClient.sendGroupAction(any(DeviceGroupActionRequest.class), eq(EXTERNAL_ID)))
                .thenReturn(response);

        grpcServerService.setState(request, responseObserver);

        ArgumentCaptor<DeviceGroupActionRequest> requestCaptor = ArgumentCaptor.forClass(DeviceGroupActionRequest.class);
        verify(yandexRestClient).sendGroupAction(requestCaptor.capture(), eq(EXTERNAL_ID));
        assertThat(requestCaptor.getValue().actions().getFirst().state().value()).isEqualTo(false);

        verify(responseObserver).onNext(any());
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
    }

    @Test
    @DisplayName("Should signal an error when Yandex rejects the action")
    void shouldHandleApiErrorResponse() {
        Yandex.SetStateRequest request = Yandex.SetStateRequest.newBuilder()
                .setExternalId(EXTERNAL_ID)
                .setOn(true)
                .setKind(Yandex.TargetKind.GROUP)
                .build();

        DeviceGroupActionResponse response = new DeviceGroupActionResponse("id", "error", List.of());
        when(yandexRestClient.sendGroupAction(any(DeviceGroupActionRequest.class), eq(EXTERNAL_ID)))
                .thenReturn(response);

        grpcServerService.setState(request, responseObserver);

        verify(responseObserver).onError(any(RuntimeException.class));
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }
}