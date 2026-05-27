package dev.iot.eventservice.service;

import dev.iot.eventservice.model.SensorData;

public interface SensorDataService {

    SensorData saveIncomingData(String deviceId, String jsonData);
}