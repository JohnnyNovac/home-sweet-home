package dev.iot.eventservice.service;

import dev.iot.eventservice.mapper.SensorDataMapper;
import dev.iot.eventservice.model.SensorData;
import dev.iot.eventservice.repository.SensorDataRepository;
import dev.iot.shared.dto.EventDTO;
import dev.iot.shared.utils.JsonDtoParser;
import org.springframework.stereotype.Service;

@Service
public class SensorDataServiceImpl implements SensorDataService {

    private final SensorDataRepository repository;
    private final SensorDataMapper sensorDataMapper;

    public SensorDataServiceImpl(
            SensorDataRepository repository,
            SensorDataMapper sensorDataMapper
    ) {
        this.repository = repository;
        this.sensorDataMapper = sensorDataMapper;
    }

    @Override
    public SensorData saveIncomingData(String deviceId, String jsonData) {
        EventDTO eventDTO = new EventDTO(deviceId, JsonDtoParser.parseMeasurements(jsonData));
        return repository.save(sensorDataMapper.toDocument(eventDTO));
    }
}