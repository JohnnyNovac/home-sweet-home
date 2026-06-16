package dev.iot.presenceservice.controller;

import dev.iot.presenceservice.service.LampService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LampController.class)
class LampControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LampService lampService;

    @Test
    @DisplayName("GET returns the current lamp state and threshold")
    void getReturnsStateAndThreshold() throws Exception {
        when(lampService.isLampOn()).thenReturn(true);
        when(lampService.getIlluminanceThreshold()).thenReturn(50.0);

        mockMvc.perform(get("/api/v1/lamp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true))
                .andExpect(jsonPath("$.illuminanceThreshold").value(50.0));
    }

    @Test
    @DisplayName("PUT /threshold updates the threshold and returns the new state")
    void putThresholdUpdatesAndReturnsState() throws Exception {
        when(lampService.getIlluminanceThreshold()).thenReturn(70.0);

        mockMvc.perform(put("/api/v1/lamp/threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"illuminanceThreshold\": 70}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.illuminanceThreshold").value(70.0));

        verify(lampService).setIlluminanceThreshold(70.0);
    }

    @Test
    @DisplayName("PUT /threshold rejects a negative threshold with 400 and does not touch the service")
    void putThresholdRejectsNegative() throws Exception {
        mockMvc.perform(put("/api/v1/lamp/threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"illuminanceThreshold\": -1}"))
                .andExpect(status().isBadRequest());

        verify(lampService, never()).setIlluminanceThreshold(anyDouble());
    }

    @Test
    @DisplayName("POST /state forces the lamp on")
    void postStateForcesLamp() throws Exception {
        mockMvc.perform(post("/api/v1/lamp/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"on\": true}"))
                .andExpect(status().isOk());

        verify(lampService).setLamp(true);
    }
}