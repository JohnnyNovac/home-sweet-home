package dev.iot.eventservice.mapper;

import dev.iot.eventservice.model.SensorData;
import dev.iot.shared.dto.CreateEventDto;
import dev.iot.shared.dto.EventDto;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SensorDataMapper {

    private final MeasurementMapper measurementMapper;

    public SensorDataMapper(MeasurementMapper measurementMapper) {
        this.measurementMapper = measurementMapper;
    }

    public SensorData toSensorData(CreateEventDto dto) {
        return new SensorData(
                dto.sensorId(),
                Instant.now(),
                dto.measurements().stream().map(measurementMapper::toMeasurement).toList()
        );
    }

    public EventDto toEventDto(SensorData sensorData) {
        return new EventDto(
                sensorData.getId(),
                sensorData.getSensorId(),
                sensorData.getTimestamp(),
                sensorData.getMeasurements().stream().map(measurementMapper::toMeasurementDto).toList()
        );
    }
}
