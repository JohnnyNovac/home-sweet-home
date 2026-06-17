package dev.iot.eventservice.controller;

import dev.iot.eventservice.service.SensorDataService;
import dev.iot.shared.dto.EventDto;
import dev.iot.shared.dto.MeasurementDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SensorDataController.class)
class SensorDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SensorDataService sensorDataService;

    @Test
    @DisplayName("POST stores a reading and returns 201")
    void postStoresReading() throws Exception {
        EventDto event = new EventDto("1", "esp01", Instant.now(),
                List.of(new MeasurementDto("temperature", 22.5, "°C")));
        when(sensorDataService.create(any())).thenReturn(event);

        mockMvc.perform(post("/api/v1/sensor-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":\"esp01\",\"measurements\":[{\"type\":\"temperature\",\"value\":22.5}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensorId").value("esp01"))
                .andExpect(jsonPath("$.measurements[0].unit").value("°C"));
    }

    @Test
    @DisplayName("GET returns the paged history")
    void getReturnsHistory() throws Exception {
        when(sensorDataService.getSensorData(0, 20))
                .thenReturn(List.of(new EventDto("1", "esp01", Instant.now(), List.of())));

        mockMvc.perform(get("/api/v1/sensor-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sensorId").value("esp01"));
    }

    @Test
    @DisplayName("DELETE clears the collection and returns 204")
    void deleteClearsCollection() throws Exception {
        mockMvc.perform(delete("/api/v1/sensor-data"))
                .andExpect(status().isNoContent());

        verify(sensorDataService).deleteAll();
    }
}