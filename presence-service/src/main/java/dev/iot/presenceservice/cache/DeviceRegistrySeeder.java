package dev.iot.presenceservice.cache;

import dev.iot.presenceservice.dto.DevicePage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.event-service.seed-enabled", matchIfMissing = true)
public class DeviceRegistrySeeder {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRegistrySeeder.class);

    private static final String DEVICES_ENDPOINT = "/api/v1/devices";

    private final DeviceRegistryCache cache;
    private final RestClient eventServiceRestClient;

    public DeviceRegistrySeeder(RestClient eventServiceRestClient, DeviceRegistryCache cache) {
        this.eventServiceRestClient = eventServiceRestClient;
        this.cache = cache;
    }

    @PostConstruct
    public void init() {
        try {
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
                                .forEach(d -> cache.upsert(d.deviceId(), d.room(), d.sensorType()));
                    }
                }
            } while (number < totalPages);
        } catch (RestClientException e) {
            logger.warn("Registry seed failed, cache will fill from live events", e);
        }
    }
}
