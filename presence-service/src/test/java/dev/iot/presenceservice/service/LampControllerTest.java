package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.GrpcClientProperties;
import dev.iot.presenceservice.config.LampProperties;
import io.grpc.StatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LampControllerTest {

    private static final double THRESHOLD = 50;

    @Mock
    private YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;

    private LampController lampController;

    @BeforeEach
    void setUp() {
        LampProperties lampProperties = new LampProperties();
        lampProperties.setIlluminanceThreshold(THRESHOLD);
        lampController = new LampController(yandexServiceStub, new GrpcClientProperties(), lampProperties);
    }

    private void stubDeadline() {
        when(yandexServiceStub.withDeadlineAfter(anyLong(), any())).thenReturn(yandexServiceStub);
    }

    @Test
    @DisplayName("Should turn the lamp on once when presence is detected and the room is dark")
    void shouldTurnOnWhenPresentAndDark() throws StatusException {
        stubDeadline();

        lampController.onIlluminance(10);
        lampController.onPresence(true);
        lampController.onPresence(true);

        verify(yandexServiceStub, times(1)).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
    }

    @Test
    @DisplayName("Should not touch the lamp while illuminance is still unknown")
    void shouldNotActWhileIlluminanceUnknown() throws StatusException {
        lampController.onPresence(true);

        verify(yandexServiceStub, never()).turnOnOffLamp(any());
    }

    @Test
    @DisplayName("Should turn the lamp on when the room darkens while someone is already present")
    void shouldTurnOnWhenRoomDarkensWhilePresent() throws StatusException {
        stubDeadline();

        lampController.onIlluminance(500);
        lampController.onPresence(true);
        verify(yandexServiceStub, never()).turnOnOffLamp(any());

        lampController.onIlluminance(10);
        verify(yandexServiceStub, times(1)).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
    }

    @Test
    @DisplayName("Should not turn the lamp off when the room becomes bright")
    void shouldNotTurnOffWhenBright() throws StatusException {
        stubDeadline();

        lampController.onIlluminance(10);
        lampController.onPresence(true);
        lampController.onIlluminance(500);

        verify(yandexServiceStub, never()).turnOnOffLamp(argThat(request -> !request.getTurnOn()));
    }

    @Test
    @DisplayName("Should turn the lamp off when presence ends")
    void shouldTurnOffWhenPresenceEnds() throws StatusException {
        stubDeadline();

        lampController.onIlluminance(10);
        lampController.onPresence(true);
        lampController.onPresence(false);

        verify(yandexServiceStub).turnOnOffLamp(argThat(request -> !request.getTurnOn()));
    }

    @Test
    @DisplayName("Should not turn the lamp off when presence ends but it was never switched on")
    void shouldNotTurnOffWhenNeverOn() throws StatusException {
        lampController.onIlluminance(500);
        lampController.onPresence(true);
        lampController.onPresence(false);

        verify(yandexServiceStub, never()).turnOnOffLamp(any());
    }

    @Test
    @DisplayName("Should retry on the next trigger after a failed lamp command")
    void shouldRetryAfterFailure() throws StatusException {
        stubDeadline();
        when(yandexServiceStub.turnOnOffLamp(any()))
                .thenThrow(new RuntimeException("yandex-service down"))
                .thenReturn(Yandex.TurnOnOffLampResponse.getDefaultInstance());

        lampController.onIlluminance(10);
        lampController.onPresence(true);  // first attempt throws, lamp stays off
        lampController.onIlluminance(9);  // still present and dark, lamp off -> retry

        verify(yandexServiceStub, times(2)).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
    }
}