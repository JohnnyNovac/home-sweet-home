package dev.iot.presenceservice.service;

import dev.iot.presenceservice.config.GrpcClientProperties;
import dev.iot.presenceservice.config.LampProperties;
import dev.iot.presenceservice.repository.LampSettingsRepository;
import io.grpc.StatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LampServiceTest {

    private static final double THRESHOLD = 50;
    private static final Duration OFF_DELAY = Duration.ofMillis(50);

    @Mock
    private YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;

    @Mock
    private LampSettingsRepository lampSettingsRepository;

    private LampService lampService;

    @BeforeEach
    void setUp() {
        LampProperties lampProperties = new LampProperties(THRESHOLD, OFF_DELAY);
        lampService = new LampService(yandexServiceStub, new GrpcClientProperties(Duration.ofSeconds(12)), lampProperties, lampSettingsRepository);
    }

    private void stubDeadline() {
        when(yandexServiceStub.withDeadlineAfter(anyLong(), any())).thenReturn(yandexServiceStub);
    }

    @Test
    @DisplayName("Should turn the lamp on once when presence is detected and the room is dark")
    void shouldTurnOnWhenPresentAndDark() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(10);
        lampService.onPresence(true);
        lampService.onPresence(true);

        verify(yandexServiceStub, times(1)).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
    }

    @Test
    @DisplayName("Should not touch the lamp while illuminance is still unknown")
    void shouldNotActWhileIlluminanceUnknown() throws StatusException {
        lampService.onPresence(true);

        verify(yandexServiceStub, never()).turnOnOffLamp(any());
    }

    @Test
    @DisplayName("Should turn the lamp on when the room darkens while someone is already present")
    void shouldTurnOnWhenRoomDarkensWhilePresent() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(500);
        lampService.onPresence(true);
        verify(yandexServiceStub, never()).turnOnOffLamp(any());

        lampService.onIlluminance(10);
        verify(yandexServiceStub, times(1)).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
    }

    @Test
    @DisplayName("Should not turn the lamp off when the room becomes bright")
    void shouldNotTurnOffWhenBright() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(10);
        lampService.onPresence(true);
        lampService.onIlluminance(500);

        verify(yandexServiceStub, never()).turnOnOffLamp(argThat(request -> !request.getTurnOn()));
    }

    @Test
    @DisplayName("Should turn the lamp off when presence ends")
    void shouldTurnOffWhenPresenceEnds() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(10);
        lampService.onPresence(true);
        lampService.onPresence(false);

        verify(yandexServiceStub, timeout(1000)).turnOnOffLamp(argThat(request -> !request.getTurnOn()));
    }

    @Test
    @DisplayName("Should not turn the lamp off when presence ends but it was never switched on")
    void shouldNotTurnOffWhenNeverOn() throws StatusException {
        lampService.onIlluminance(500);
        lampService.onPresence(true);
        lampService.onPresence(false);

        verify(yandexServiceStub, never()).turnOnOffLamp(any());
    }

    @Test
    @DisplayName("Should retry on the next trigger after a failed lamp command")
    void shouldRetryAfterFailure() throws StatusException {
        stubDeadline();
        when(yandexServiceStub.turnOnOffLamp(any()))
                .thenThrow(new RuntimeException("yandex-service down"))
                .thenReturn(Yandex.TurnOnOffLampResponse.getDefaultInstance());

        lampService.onIlluminance(10);
        lampService.onPresence(true);  // first attempt throws, lamp stays off
        lampService.onIlluminance(9);  // still present and dark, lamp off -> retry

        verify(yandexServiceStub, times(2)).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
    }

    @Test
    @DisplayName("Should persist a new threshold and reevaluate so a now-dark room turns the lamp on")
    void shouldReactToThresholdChange() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(60);
        lampService.onPresence(true);
        verify(yandexServiceStub, never()).turnOnOffLamp(any());  // 60 >= 50, not dark

        lampService.setIlluminanceThreshold(70);  // 60 < 70, now dark and present

        verify(lampSettingsRepository).save(argThat(s -> s.id().equals("illuminanceThreshold") && s.value() == 70));
        verify(yandexServiceStub, times(1)).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
    }

    @Test
    @DisplayName("Should not turn the lamp off when presence returns before the off-delay elapses")
    void shouldNotTurnOffWhenPresenceReturnsBeforeDelay() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(10);
        lampService.onPresence(true);
        lampService.onPresence(false);  // schedules a delayed off
        lampService.onPresence(true);   // returns in time, cancels it

        verify(yandexServiceStub, after(OFF_DELAY.toMillis() + 200).never())
                .turnOnOffLamp(argThat(request -> !request.getTurnOn()));
    }

    @Test
    @DisplayName("Should persist a new off-delay")
    void shouldPersistOffDelay() {
        lampService.setLampOffDelay(Duration.ofSeconds(30));

        verify(lampSettingsRepository).save(argThat(s -> s.id().equals("lampOffDelay") && s.value() == 30));
    }

    @Test
    @DisplayName("Should force the lamp on")
    void shouldForceLampOn() throws StatusException {
        stubDeadline();

        lampService.setLamp(true);

        verify(yandexServiceStub).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
        assertThat(lampService.isLampOn()).isTrue();
    }

    @Test
    @DisplayName("Should force the lamp off")
    void shouldForceLampOff() throws StatusException {
        stubDeadline();

        lampService.setLamp(false);

        verify(yandexServiceStub).turnOnOffLamp(argThat(request -> !request.getTurnOn()));
        assertThat(lampService.isLampOn()).isFalse();
    }

    @Test
    @DisplayName("Should keep the lamp off when the force command fails")
    void shouldNotForceLampOnWhenCommandFails() throws StatusException {
        stubDeadline();
        when(yandexServiceStub.turnOnOffLamp(any())).thenThrow(new RuntimeException("down"));

        lampService.setLamp(true);

        verify(yandexServiceStub).turnOnOffLamp(argThat(Yandex.TurnOnOffLampRequest::getTurnOn));
        assertThat(lampService.isLampOn()).isFalse();
    }

    @Test
    @DisplayName("Should not reschedule the off when one is already pending")
    void shouldNotRescheduleOffWhenAlreadyPending() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(10);
        lampService.onPresence(true);   // lamp on
        lampService.onPresence(false);  // schedules the delayed off
        lampService.onPresence(false);  // off already pending -> early return

        verify(yandexServiceStub, timeout(1000).times(1)).turnOnOffLamp(argThat(request -> !request.getTurnOn()));
    }

    @Test
    @DisplayName("Should keep the lamp on when the delayed off command fails")
    void shouldKeepLampOnWhenDelayedOffCommandFails() throws StatusException {
        stubDeadline();
        // Only the OFF call fails; the ON call is left to the default answer, so the stub is lenient
        // (turnOnOffLamp is also invoked with a non-matching ON argument, which strict stubs would flag).
        lenient().when(yandexServiceStub.turnOnOffLamp(argThat(request -> !request.getTurnOn())))
                .thenThrow(new RuntimeException("down"));

        lampService.onIlluminance(10);
        lampService.onPresence(true);   // lamp on
        lampService.onPresence(false);  // after the delay the OFF call fails

        verify(yandexServiceStub, timeout(1000)).turnOnOffLamp(argThat(request -> !request.getTurnOn()));
        assertThat(lampService.isLampOn()).isTrue();
    }
}