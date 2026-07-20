package dev.iot.presenceservice.controller;

import dev.iot.presenceservice.service.LampService;
import dev.iot.websupport.CommonExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LampController.class)
@Import(CommonExceptionHandler.class)
class LampControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LampService lampService;

    @Test
    @DisplayName("GET returns the current lamp state, threshold and off-delay")
    void getReturnsState() throws Exception {
        when(lampService.isLampOn()).thenReturn(true);
        when(lampService.getIlluminanceThreshold()).thenReturn(50.0);
        when(lampService.getLampOffDelay()).thenReturn(15L);

        mockMvc.perform(get("/api/v1/lamp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.on").value(true))
                .andExpect(jsonPath("$.illuminanceThreshold").value(50.0))
                .andExpect(jsonPath("$.offDelay").value(15));
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
    @DisplayName("PUT /threshold rejects a negative threshold with a 400 validation error")
    void putThresholdRejectsNegative() throws Exception {
        mockMvc.perform(put("/api/v1/lamp/threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"illuminanceThreshold\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("illuminanceThreshold"));

        verify(lampService, never()).setIlluminanceThreshold(anyDouble());
    }

    @Test
    @DisplayName("PUT /off-delay updates the off-delay and returns the new state")
    void putOffDelayUpdatesAndReturnsState() throws Exception {
        when(lampService.getLampOffDelay()).thenReturn(30L);

        mockMvc.perform(put("/api/v1/lamp/off-delay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offDelay\": 30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offDelay").value(30));

        verify(lampService).setLampOffDelay(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("PUT /off-delay rejects a non-positive delay with a 400 validation error")
    void putOffDelayRejectsNonPositive() throws Exception {
        mockMvc.perform(put("/api/v1/lamp/off-delay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offDelay\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("offDelay"));

        verify(lampService, never()).setLampOffDelay(any());
    }

    @Test
    @DisplayName("POST /state forces the lamp on")
    void postStateForcesLamp() throws Exception {
        mockMvc.perform(post("/api/v1/lamp/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"room\": \"living-room\", \"on\": true}"))
                .andExpect(status().isOk());

        verify(lampService).setLamp("living-room", true);
    }
}