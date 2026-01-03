package dev.iot.eventservice.service;

import dev.iot.eventservice.mapper.SensorDataMapper;
import dev.iot.eventservice.model.SensorData;
import dev.iot.eventservice.repository.SensorDataRepository;
import dev.iot.shared.dto.EventDTO;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import dev.iot.shared.utils.JsonDtoParser;

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
    public Mono<SensorData> saveIncomingData(String jsonData) {
        EventDTO eventDTO = JsonDtoParser.parseJson(jsonData);
        return repository.save(sensorDataMapper.toDocument(eventDTO));
    }

}
