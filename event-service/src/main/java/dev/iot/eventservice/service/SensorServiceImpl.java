package dev.iot.eventservice.service;

import dev.iot.eventservice.mapper.SensorDataMapper;
import dev.iot.eventservice.model.SensorData;
import dev.iot.eventservice.repository.SensorDataRepository;
import dev.iot.shared.dto.EventDTO;
import dev.iot.shared.dto.MeasurementDTO;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import utils.JsonDtoParser;

import java.util.List;

@Service
public class SensorServiceImpl implements SensorService {

    private final SensorDataRepository repository;
    private final SensorDataMapper sensorDataMapper;

    public SensorServiceImpl(
            SensorDataRepository repository,
            SensorDataMapper sensorDataMapper
    ) {
        this.repository = repository;
        this.sensorDataMapper = sensorDataMapper;
    }

    @Override
    public Mono<SensorData> saveIncomingData(String sensorId, String jsonData) {
        List<MeasurementDTO> measurementDTOs = JsonDtoParser.parseMeasurements(jsonData);
        EventDTO eventDTO = new EventDTO(sensorId, measurementDTOs);
        return repository.save(sensorDataMapper.toDocument(eventDTO));
    }

}
