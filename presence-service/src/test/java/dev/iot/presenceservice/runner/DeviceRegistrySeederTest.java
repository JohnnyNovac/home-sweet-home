package dev.iot.presenceservice.runner;

import dev.iot.presenceservice.cache.DeviceRegistryCache;
import dev.iot.presenceservice.config.EventServiceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
public class DeviceRegistrySeederTest {

    private static final String BASE_URL = "http://event-service:8081";
    private static final EventServiceProperties PROPERTIES = new EventServiceProperties(BASE_URL, Duration.ofSeconds(30));

    @Mock
    private DeviceRegistryCache cache;

    @Test
    @DisplayName("Should seed the cache from a single page")
    void shouldSeedFromSinglePage() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        String json = """
                {
                  "content": [
                    {"deviceId":"lamp-1","deviceType":"lamp","roomId":"bedroom","name":null,"lastSeenAt":null,"externalId":"bulb-1","externalKind":"DEVICE","groupExternalIds":["chandelier-7"]}
                  ],
                  "page": {"size":20,"number":0,"totalElements":1,"totalPages":1}
                }
                """;
        mockServer.expect(requestTo(BASE_URL + "/api/v1/devices?pageNumber=0"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        DeviceRegistrySeeder seeder = new DeviceRegistrySeeder(builder.build(), cache, PROPERTIES);
        seeder.attemptSeed();

        mockServer.verify();
        verify(cache).putIfAbsent("lamp-1", "bedroom", "lamp", "bulb-1", "DEVICE", List.of("chandelier-7"));
    }

    @Test
    @DisplayName("Should follow pagination across every page")
    void shouldFollowPagination() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        String firstPage = """
                {
                  "content": [
                    {"deviceId":"esp01-1","deviceType":"climate","roomId":"bedroom","name":null,"lastSeenAt":null}
                  ],
                  "page": {"size":1,"number":0,"totalElements":2,"totalPages":2}
                }
                """;
        String secondPage = """
                {
                  "content": [
                    {"deviceId":"esp01-2","deviceType":"climate","roomId":"kitchen","name":null,"lastSeenAt":null}
                  ],
                  "page": {"size":1,"number":1,"totalElements":2,"totalPages":2}
                }
                """;
        mockServer.expect(requestTo(BASE_URL + "/api/v1/devices?pageNumber=0"))
                .andRespond(withSuccess(firstPage, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/api/v1/devices?pageNumber=1"))
                .andRespond(withSuccess(secondPage, MediaType.APPLICATION_JSON));

        DeviceRegistrySeeder seeder = new DeviceRegistrySeeder(builder.build(), cache, PROPERTIES);
        seeder.attemptSeed();

        mockServer.verify();
        verify(cache).putIfAbsent("esp01-1", "bedroom", "climate", null, null, null);
        verify(cache).putIfAbsent("esp01-2", "kitchen", "climate", null, null, null);
    }

    @Test
    @DisplayName("Should skip devices without a room")
    void shouldSkipDevicesWithoutRoom() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        String json = """
                {
                  "content": [
                    {"deviceId":"esp01-1","deviceType":"climate","roomId":"bedroom","name":null,"lastSeenAt":null},
                    {"deviceId":"esp01-2","deviceType":"climate","roomId":null,"name":null,"lastSeenAt":null}
                  ],
                  "page": {"size":20,"number":0,"totalElements":2,"totalPages":1}
                }
                """;
        mockServer.expect(requestTo(BASE_URL + "/api/v1/devices?pageNumber=0"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        DeviceRegistrySeeder seeder = new DeviceRegistrySeeder(builder.build(), cache, PROPERTIES);
        seeder.attemptSeed();

        mockServer.verify();
        verify(cache).putIfAbsent("esp01-1", "bedroom", "climate", null, null, null);
        verify(cache, never()).putIfAbsent(eq("esp01-2"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should swallow a server error and leave the cache empty")
    void shouldSwallowServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo(BASE_URL + "/api/v1/devices?pageNumber=0"))
                .andRespond(withServerError());

        DeviceRegistrySeeder seeder = new DeviceRegistrySeeder(builder.build(), cache, PROPERTIES);
        seeder.attemptSeed();

        mockServer.verify();
        verifyNoInteractions(cache);
    }
}