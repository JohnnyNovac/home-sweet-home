package dev.iot.presenceservice.runner;

import dev.iot.presenceservice.cache.DeviceRegistryCache;
import dev.iot.presenceservice.config.EventServiceProperties;
import dev.iot.presenceservice.dto.DevicePage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.event-service.seed-enabled", matchIfMissing = true)
public class DeviceRegistrySeeder {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRegistrySeeder.class);

    private static final String DEVICES_ENDPOINT = "/api/v1/devices";

    private final DeviceRegistryCache cache;
    private final RestClient eventServiceRestClient;
    private final EventServiceProperties eventServiceProperties;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DeviceRegistrySeeder(RestClient eventServiceRestClient, DeviceRegistryCache cache,
                                EventServiceProperties eventServiceProperties) {
        this.eventServiceRestClient = eventServiceRestClient;
        this.cache = cache;
        this.eventServiceProperties = eventServiceProperties;
    }

    @PostConstruct
    public void init() {
        long retryDelay = eventServiceProperties.seedRetryDelay().toMillis();
        scheduler.scheduleWithFixedDelay(this::attemptSeed, 0, retryDelay, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }

    // an exception escaping this method would silently cancel the periodic retry
    public void attemptSeed() {
        try {
            seed();
            logger.info("Device registry seeded from event-service");
            scheduler.shutdown();
        } catch (Exception e) {
            logger.warn("Registry seed failed, next attempt in {}", eventServiceProperties.seedRetryDelay(), e);
        }
    }

    private void seed() {
        int number = 0;
        int totalPages = 0;
        do {
            int currentPage = number;
            DevicePage page = eventServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path(DEVICES_ENDPOINT)
                            .queryParam("pageNumber", currentPage)
                            .build())
                    .retrieve()
                    .body(DevicePage.class);
            number++;
            if (page != null) {
                totalPages = (int) page.pageMetadata().totalPages();
                if (page.content() != null && !page.content().isEmpty()) {
                    page.content().stream()
                            .filter(d -> d.room() != null)
                            .forEach(d -> cache.putIfAbsent(
                                    d.deviceId(),
                                    d.room(),
                                    d.sensorType(),
                                    d.externalId(),
                                    d.externalKind(),
                                    d.groupExternalIds()
                            ));
                }
            }
        } while (number < totalPages);
    }
}