package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceEntry;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LampServiceTest {

    private static final double THRESHOLD = 50;
    private static final Duration OFF_DELAY = Duration.ofMillis(50);
    private static final Duration SYNC_GAP = Duration.ofSeconds(90);
    private static final String ROOM = "living-room";
    private static final List<DeviceEntry> LAMPS = List.of(new DeviceEntry(ROOM, "lamp", "chandelier-1", "GROUP", List.of()));

    @Mock
    private YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;

    @Mock
    private LampSettingsRepository lampSettingsRepository;

    @Mock
    private LampGate lampGate;

    private LampService lampService;

    @BeforeEach
    void setUp() {
        LampProperties lampProperties = new LampProperties(THRESHOLD, OFF_DELAY, SYNC_GAP);
        lampService = new LampService(yandexServiceStub, new GrpcClientProperties(Duration.ofSeconds(12)), lampProperties, lampSettingsRepository, lampGate);
    }

    private void stubDeadline() {
        when(yandexServiceStub.withDeadlineAfter(anyLong(), any())).thenReturn(yandexServiceStub);
    }

    @Test
    @DisplayName("Should turn the lamp on once when presence is detected and the room is dark")
    void shouldTurnOnWhenPresentAndDark() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(ROOM, 10);
        lampService.onPresence(ROOM, LAMPS, true);
        lampService.onPresence(ROOM, LAMPS, true);

        verify(yandexServiceStub, times(1)).setState(argThat(Yandex.SetStateRequest::getOn));
    }

    @Test
    @DisplayName("Should not touch the lamp while illuminance is still unknown")
    void shouldNotActWhileIlluminanceUnknown() throws StatusException {
        lampService.onPresence(ROOM, LAMPS, true);

        verify(yandexServiceStub, never()).setState(any());
    }

    @Test
    @DisplayName("Should turn the lamp on when the room darkens while someone is already present")
    void shouldTurnOnWhenRoomDarkensWhilePresent() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(ROOM, 500);
        lampService.onPresence(ROOM, LAMPS, true);
        verify(yandexServiceStub, never()).setState(any());

        lampService.onIlluminance(ROOM, 10);
        verify(yandexServiceStub, times(1)).setState(argThat(Yandex.SetStateRequest::getOn));
    }

    @Test
    @DisplayName("Should not turn the lamp off when the room becomes bright")
    void shouldNotTurnOffWhenBright() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(ROOM, 10);
        lampService.onPresence(ROOM, LAMPS, true);
        lampService.onIlluminance(ROOM, 500);

        verify(yandexServiceStub, never()).setState(argThat(request -> !request.getOn()));
    }

    @Test
    @DisplayName("Should turn the lamp off when presence ends")
    void shouldTurnOffWhenPresenceEnds() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(ROOM, 10);
        lampService.onPresence(ROOM, LAMPS, true);
        lampService.onPresence(ROOM, LAMPS, false);

        verify(yandexServiceStub, timeout(1000)).setState(argThat(request -> !request.getOn()));
    }

    @Test
    @DisplayName("Should not turn the lamp off when presence ends but it was never switched on")
    void shouldNotTurnOffWhenNeverOn() throws StatusException {
        lampService.onIlluminance(ROOM, 500);
        lampService.onPresence(ROOM, LAMPS, true);
        lampService.onPresence(ROOM, LAMPS, false);

        verify(yandexServiceStub, never()).setState(any());
    }

    @Test
    @DisplayName("Should retry on the next trigger after a failed lamp command")
    void shouldRetryAfterFailure() throws StatusException {
        stubDeadline();
        when(yandexServiceStub.setState(any()))
                .thenThrow(new RuntimeException("yandex-service down"))
                .thenReturn(Yandex.SetStateResponse.getDefaultInstance());

        lampService.onIlluminance(ROOM, 10);
        lampService.onPresence(ROOM, LAMPS, true);  // first attempt throws, lamp stays off
        lampService.onIlluminance(ROOM, 9);         // still present and dark, lamp off -> retry

        verify(yandexServiceStub, times(2)).setState(argThat(Yandex.SetStateRequest::getOn));
    }

    @Test
    @DisplayName("Should persist a new threshold and reevaluate so a now-dark room turns the lamp on")
    void shouldReactToThresholdChange() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(ROOM, 60);
        lampService.onPresence(ROOM, LAMPS, true);
        verify(yandexServiceStub, never()).setState(any());  // 60 >= 50, not dark

        lampService.setIlluminanceThreshold(70);  // 60 < 70, now dark and present

        verify(lampSettingsRepository).save(argThat(s -> s.id().equals("illuminanceThreshold") && s.value() == 70));
        verify(yandexServiceStub, times(1)).setState(argThat(Yandex.SetStateRequest::getOn));
    }

    @Test
    @DisplayName("Should not turn the lamp off when presence returns before the off-delay elapses")
    void shouldNotTurnOffWhenPresenceReturnsBeforeDelay() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(ROOM, 10);
        lampService.onPresence(ROOM, LAMPS, true);
        lampService.onPresence(ROOM, LAMPS, false);  // schedules a delayed off
        lampService.onPresence(ROOM, LAMPS, true);   // returns in time, cancels it

        verify(yandexServiceStub, after(OFF_DELAY.toMillis() + 200).never())
                .setState(argThat(request -> !request.getOn()));
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
        when(lampGate.lampsForRoom(ROOM)).thenReturn(LAMPS);

        lampService.setLamp(ROOM, true);

        verify(yandexServiceStub).setState(argThat(Yandex.SetStateRequest::getOn));
        assertThat(lampService.isLampOn()).isTrue();
    }

    @Test
    @DisplayName("Should force the lamp off")
    void shouldForceLampOff() throws StatusException {
        stubDeadline();
        when(lampGate.lampsForRoom(ROOM)).thenReturn(LAMPS);

        lampService.setLamp(ROOM, false);

        verify(yandexServiceStub).setState(argThat(request -> !request.getOn()));
        assertThat(lampService.isLampOn()).isFalse();
    }

    @Test
    @DisplayName("Should keep the lamp off when the force command fails")
    void shouldNotForceLampOnWhenCommandFails() throws StatusException {
        stubDeadline();
        when(lampGate.lampsForRoom(ROOM)).thenReturn(LAMPS);
        when(yandexServiceStub.setState(any())).thenThrow(new RuntimeException("down"));

        lampService.setLamp(ROOM, true);

        verify(yandexServiceStub).setState(argThat(Yandex.SetStateRequest::getOn));
        assertThat(lampService.isLampOn()).isFalse();
    }

    @Test
    @DisplayName("Should not reschedule the off when one is already pending")
    void shouldNotRescheduleOffWhenAlreadyPending() throws StatusException {
        stubDeadline();

        lampService.onIlluminance(ROOM, 10);
        lampService.onPresence(ROOM, LAMPS, true);   // lamp on
        lampService.onPresence(ROOM, LAMPS, false);  // schedules the delayed off
        lampService.onPresence(ROOM, LAMPS, false);  // off already pending -> early return

        verify(yandexServiceStub, timeout(1000).times(1)).setState(argThat(request -> !request.getOn()));
    }

    @Test
    @DisplayName("Should keep the lamp on when the delayed off command fails")
    void shouldKeepLampOnWhenDelayedOffCommandFails() throws StatusException {
        stubDeadline();
        // Only the OFF call fails; the ON call is left to the default answer, so the stub is lenient
        // (setState is also invoked with a non-matching ON argument, which strict stubs would flag).
        lenient().when(yandexServiceStub.setState(argThat(request -> !request.getOn())))
                .thenThrow(new RuntimeException("down"));

        lampService.onIlluminance(ROOM, 10);
        lampService.onPresence(ROOM, LAMPS, true);   // lamp on
        lampService.onPresence(ROOM, LAMPS, false);  // after the delay the OFF call fails

        verify(yandexServiceStub, timeout(1000)).setState(argThat(request -> !request.getOn()));
        assertThat(lampService.isLampOn()).isTrue();
    }
}