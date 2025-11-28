package dev.iot.eventservice.mapper;

import dev.iot.eventservice.model.SensorData;
import dev.iot.shared.dto.EventDTO;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SensorDataMapper {

    private final MeasurementMapper measurementMapper;

    public SensorDataMapper(MeasurementMapper measurementMapper) {
        this.measurementMapper = measurementMapper;
    }

    public SensorData toDocument(EventDTO dto) {
        return new SensorData(
                dto.sensorId(),
                Instant.now(),
                dto.measurements().stream().map(measurementMapper::toEntity).toList()
        );
    }

}
