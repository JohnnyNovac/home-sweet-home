package dev.iot.presenceservice.service;

import dev.iot.presenceservice.cache.DeviceEntry;
import dev.iot.presenceservice.config.GrpcClientProperties;
import dev.iot.presenceservice.config.LampProperties;
import dev.iot.presenceservice.model.RoomState;
import dev.iot.presenceservice.model.LampSettings;
import dev.iot.presenceservice.repository.LampSettingsRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Stateful lamp decision engine. The lamp turns on only when presence is detected AND the room is
 * dark (illuminance below the configured threshold); it turns off when presence ends. Brightness
 * never turns the lamp off. Presence and illuminance arrive from two independent listeners, so the
 * latest value of each is held here per room, and only the room a message belongs to is recomputed
 * whenever either changes — this is what lets the lamp come on when the room darkens while someone
 * is already present. Both inputs are unknown ({@code null}) until their first message arrives, and
 * the lamp is only switched on once both are known. Access is synchronised because the two listeners
 * run on separate threads; the lamp command is rare and bounded by the gRPC deadline, so holding the
 * lock across it is fine.
 */
@Service
public class LampService {

    private static final Logger logger = LoggerFactory.getLogger(LampService.class);

    private static final String ILLUMINANCE_THRESHOLD_SETTING_ID = "illuminanceThreshold";
    private static final String LAMP_OFF_DELAY_SETTING_ID = "lampOffDelay";

    private final YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;
    private final GrpcClientProperties grpcClientProperties;
    private final LampSettingsRepository lampSettingsRepository;
    private final LampGate lampGate;

    private final Map<String, RoomState> roomsState = new HashMap<>();
    private double illuminanceThreshold;
    private Duration lampOffDelay;
    private final Duration lampStateSyncGap;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public LampService(
            YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub,
            GrpcClientProperties grpcClientProperties,
            LampProperties lampProperties,
            LampSettingsRepository lampSettingsRepository,
            LampGate lampGate
    ) {
        this.yandexServiceStub = yandexServiceStub;
        this.grpcClientProperties = grpcClientProperties;
        this.lampSettingsRepository = lampSettingsRepository;
        this.illuminanceThreshold = lampProperties.illuminanceThreshold();
        this.lampOffDelay = lampProperties.lampOffDelay();
        this.lampGate = lampGate;
        this.lampStateSyncGap = lampProperties.lampStateSyncGap();
    }

    @PostConstruct
    synchronized void loadSettings() {
        lampSettingsRepository.findById(ILLUMINANCE_THRESHOLD_SETTING_ID).ifPresentOrElse(
                settings -> illuminanceThreshold = settings.value(),
                () -> lampSettingsRepository.save(new LampSettings(ILLUMINANCE_THRESHOLD_SETTING_ID, illuminanceThreshold))
        );
        lampSettingsRepository.findById(LAMP_OFF_DELAY_SETTING_ID).ifPresentOrElse(
                settings -> lampOffDelay = Duration.ofSeconds((long) settings.value()),
                () -> lampSettingsRepository.save(new LampSettings(LAMP_OFF_DELAY_SETTING_ID, lampOffDelay.toSeconds()))
        );
        logger.info("Lamp illuminance threshold is {} lx, off-delay is {} s", illuminanceThreshold, lampOffDelay.toSeconds());
    }

    public synchronized void onPresence(String roomId, List<DeviceEntry> lamps, boolean present) {
        RoomState roomState = roomState(roomId);
        roomState.setLamps(lamps);
        roomState.setPresent(present);
        if (present) {
            Instant now = Instant.now();
            Instant lastPresentAt = roomState.getLastPresentAt();
            roomState.setLastPresentAt(now);
            boolean stale = (lastPresentAt == null || Duration.between(lastPresentAt, now).compareTo(lampStateSyncGap) > 0);
            if (stale) {
                syncLampState(roomId, roomState);
            }
            cancelPendingOff(roomState);
            reevaluate(roomState, roomId, "presence");
        } else {
            scheduleLampOff(roomState);
        }
    }

    public synchronized void onIlluminance(String roomId, double illuminance) {
        RoomState roomState = roomState(roomId);
        roomState.setIlluminance(illuminance);
        reevaluate(roomState, roomId, "illuminance");
    }

    private void reevaluate(RoomState roomState, String roomId, String trigger) {
        boolean shouldTurnOn = Boolean.TRUE.equals(roomState.getPresent()) && isDark(roomState) && !roomState.isLampOn();
        logger.debug("Reevaluating lamp in room {} (triggered by {}): present={}, illuminance={}, lampOn={} -> {}",
                roomId, trigger, roomState.getPresent(), roomState.getIlluminance(), roomState.isLampOn(), shouldTurnOn ? "turn on" : "no change");
        if (shouldTurnOn && turnLamps(roomState.getLamps(), true)) {
            roomState.setLampOn(true);
        }
    }

    private void scheduleLampOff(RoomState roomState) {
        if (!roomState.isLampOn() || roomState.getPendingLampOff() != null) {
            return;
        }
        roomState.setPendingLampOff(scheduler.schedule(() -> turnOffAfterDelay(roomState), lampOffDelay.toSeconds(), TimeUnit.SECONDS));
    }

    // runs on the scheduler thread — must re-check presence, the cancel may have lost the race
    private synchronized void turnOffAfterDelay(RoomState roomState) {
        roomState.setPendingLampOff(null);
        if (Boolean.FALSE.equals(roomState.getPresent()) && roomState.isLampOn()
                && turnLamps(roomState.getLamps(), false)) {
            roomState.setLampOn(false);
        }
    }

    private void cancelPendingOff(RoomState roomState) {
        if (roomState.getPendingLampOff() != null) {
            roomState.getPendingLampOff().cancel(false);
            roomState.setPendingLampOff(null);
        }
    }

    public synchronized double getIlluminanceThreshold() {
        return illuminanceThreshold;
    }

    public synchronized void setIlluminanceThreshold(double threshold) {
        illuminanceThreshold = threshold;
        lampSettingsRepository.save(new LampSettings(ILLUMINANCE_THRESHOLD_SETTING_ID, threshold));
        logger.info("Lamp illuminance threshold changed to {} lx", threshold);
        roomsState.forEach((roomId, roomState) -> reevaluate(roomState, roomId, "threshold change"));
    }

    public synchronized long getLampOffDelay() {
        return lampOffDelay.toSeconds();
    }

    public synchronized void setLampOffDelay(Duration offDelay) {
        lampOffDelay = offDelay;
        lampSettingsRepository.save(new LampSettings(LAMP_OFF_DELAY_SETTING_ID, offDelay.toSeconds()));
        logger.info("Lamp off-delay changed to {} s", offDelay.toSeconds());
    }

    public synchronized void setLamp(String roomId, boolean on) {
        List<DeviceEntry> lamps = lampGate.lampsForRoom(roomId);
        if (lamps.isEmpty()) {
            return;                       // no group-lamps in the room — nothing to force
        }
        RoomState roomState = roomState(roomId);
        roomState.setLamps(lamps);
        if (turnLamps(roomState.getLamps(), on)) {
            roomState.setLampOn(on);
        }
    }

    public synchronized boolean isLampOn() {
        return roomsState.values().stream().anyMatch(RoomState::isLampOn);
    }

    private boolean isDark(RoomState roomState) {
        return roomState.getIlluminance() != null && roomState.getIlluminance() < illuminanceThreshold;
    }

    // opportunistic: any failure aborts the whole sync and keeps the tracked lampOn untouched
    private void syncLampState(String roomId, RoomState roomState) {
        boolean anyOn = false;
        for (DeviceEntry lamp : roomState.getLamps()) {
            Yandex.GetStateRequest request = Yandex.GetStateRequest.newBuilder()
                    .setExternalId(lamp.externalId())
                    .setKind(Yandex.TargetKind.GROUP)
                    .build();
            try {
                if (yandexServiceStub.getState(request).getOn()) {
                    anyOn = true;
                    break;
                }
            } catch (Exception e) {
                logger.error("Failed to read lamp {} state in room {}, keeping lampOn={}",
                        lamp.externalId(), roomId, roomState.isLampOn(), e);
                return;
            }
        }
        roomState.setLampOn(anyOn);
        logger.info("Lamp state synced for room {}: lampOn={}", roomId, anyOn);
    }

    private boolean turnLamps(List<DeviceEntry> lamps, boolean on) {
        boolean allOk = true;
        for (DeviceEntry lamp : lamps) {
            Yandex.SetStateRequest request = Yandex.SetStateRequest.newBuilder()
                    .setExternalId(lamp.externalId())
                    .setOn(on)
                    .setKind(Yandex.TargetKind.GROUP)
                    .build();

            logger.info("Setting lamp {} state to {}", lamp.externalId(), on ? "ON" : "OFF");

            try {
                yandexServiceStub
                        .withDeadlineAfter(grpcClientProperties.lampDeadline().toMillis(), TimeUnit.MILLISECONDS)
                        .setState(request);
            } catch (Exception e) {
                logger.error("Failed to change lamp {} state", lamp.externalId(), e);
                allOk = false;
            }
        }
        return allOk;
    }

    private RoomState roomState(String roomId) {
        return roomsState.computeIfAbsent(roomId, r -> new RoomState());
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}