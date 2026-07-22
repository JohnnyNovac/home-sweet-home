package dev.iot.presenceservice.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

public class DevicePageTest {

    @Test
    @DisplayName("Should deserialize a PagedModel JSON page into DevicePage")
    void shouldDeserializePageModelJson() {
        String json = """
              {
                "content": [
                  {"deviceId":"esp01-1","deviceType":"climate","roomId":"bedroom","name":"ESP-01-1","lastSeenAt":null}
                ],
                "page": {"size":20,"number":0,"totalElements":1,"totalPages":1}
              }
              """;
        ObjectMapper mapper = new ObjectMapper();   // tools.jackson.databind.ObjectMapper
        DevicePage page = mapper.readValue(json, DevicePage.class);

        assertThat(page.pageMetadata().totalPages()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().roomId()).isEqualTo("bedroom");
    }
}
