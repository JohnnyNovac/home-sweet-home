package dev.iot.eventservice.service;

import dev.iot.eventservice.mapper.SensorDataMapper;
import dev.iot.eventservice.model.SensorData;
import dev.iot.eventservice.repository.SensorDataRepository;
import dev.iot.shared.dto.CreateEventDto;
import dev.iot.shared.dto.EventDto;
import dev.iot.shared.utils.JsonDtoParser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SensorDataService {

    private final SensorDataRepository repository;
    private final SensorDataMapper sensorDataMapper;

    public SensorDataService(
            SensorDataRepository repository,
            SensorDataMapper sensorDataMapper
    ) {
        this.repository = repository;
        this.sensorDataMapper = sensorDataMapper;
    }

    public SensorData saveIncomingData(String deviceId, String jsonData) {
        CreateEventDto createEventDTO = new CreateEventDto(deviceId, JsonDtoParser.parseMeasurements(jsonData));
        return repository.save(sensorDataMapper.toSensorData(createEventDTO));
    }

    public EventDto create(CreateEventDto dto) {
        SensorData sensorData = repository.save(sensorDataMapper.toSensorData(dto));
        return sensorDataMapper.toEventDto(sensorData);
    }

    public List<EventDto> getSensorData(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findAll(pageable).stream().map(sensorDataMapper::toEventDto).toList();
    }

    public void deleteAll() {
        repository.deleteAll();
    }
}