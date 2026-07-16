package dev.iot.eventservice.controller;

import dev.iot.eventservice.dto.DeviceDto;
import dev.iot.eventservice.exception.DeviceAlreadyExistsException;
import dev.iot.eventservice.exception.DeviceNotFoundException;
import dev.iot.eventservice.service.DeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceService deviceService;

    @Test
    @DisplayName("POST creates a device and returns 201")
    void postCreatesDevice() throws Exception {
        when(deviceService.create(any()))
                .thenReturn(new DeviceDto("esp01", "climate", "bedroom", "NodeMCU-1", null, null, null));

        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"esp01\",\"sensorType\":\"climate\",\"room\":\"bedroom\",\"name\":\"NodeMCU-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId").value("esp01"))
                .andExpect(jsonPath("$.room").value("bedroom"));
    }

    @Test
    @DisplayName("POST of a duplicate device returns 409")
    void postDuplicateReturnsConflict() throws Exception {
        when(deviceService.create(any())).thenThrow(new DeviceAlreadyExistsException("esp01"));

        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"esp01\",\"sensorType\":\"climate\",\"room\":\"bedroom\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DEVICE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST without the required sensorType/room returns 400 and does not touch the service")
    void postMissingRequiredFieldsReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"esp01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        verify(deviceService, never()).create(any());
    }

    @Test
    @DisplayName("GET returns the paged device list")
    void getReturnsDevices() throws Exception {
        when(deviceService.getDevices(0, 20))
                .thenReturn(new PageImpl<>(List.of(new DeviceDto("esp01", "climate", "bedroom", "NodeMCU-1", null, null, null))));

        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].deviceId").value("esp01"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT updates room/name and returns 200")
    void putUpdatesDevice() throws Exception {
        when(deviceService.update(eq("esp01"), any()))
                .thenReturn(new DeviceDto("esp01", "climate", "kitchen", "NodeMCU-2", null, null, null));

        mockMvc.perform(put("/api/v1/devices/esp01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"room\":\"kitchen\",\"name\":\"NodeMCU-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room").value("kitchen"));
    }

    @Test
    @DisplayName("PUT of an unknown device returns 404")
    void putUnknownReturnsNotFound() throws Exception {
        when(deviceService.update(eq("ghost"), any())).thenThrow(new DeviceNotFoundException("ghost"));

        mockMvc.perform(put("/api/v1/devices/ghost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"room\":\"kitchen\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DEVICE_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE removes the device and returns 204")
    void deleteRemovesDevice() throws Exception {
        mockMvc.perform(delete("/api/v1/devices/esp01"))
                .andExpect(status().isNoContent());

        verify(deviceService).delete("esp01");
    }
}