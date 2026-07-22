package dev.iot.presenceservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param illuminanceThreshold illuminance (lux) below which the room is considered dark enough to turn the lamp on
 * @param lampOffDelay         how long presence must stay absent before the lamp is switched off; presence returning
 *                             within this window cancels the pending switch-off
 * @param lampStateSyncGap     presence gap after which the tracked lamp state is re-read from Yandex before deciding;
 *                             kept above the 60s presence heartbeat so normal traffic never triggers the sync
 */
@ConfigurationProperties(prefix = "app.lamp")
public record LampProperties(
        @DefaultValue("50") double illuminanceThreshold,
        @DefaultValue("15s") Duration lampOffDelay,
        @DefaultValue("90s") Duration lampStateSyncGap
) {
}