package dev.iot.eventservice.service;

import dev.iot.eventservice.model.SensorData;
import reactor.core.publisher.Mono;

public interface SensorDataService {

    Mono<SensorData> saveIncomingData(String deviceId, String jsonData);
}