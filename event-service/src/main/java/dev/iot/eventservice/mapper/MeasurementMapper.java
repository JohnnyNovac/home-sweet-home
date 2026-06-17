package dev.iot.eventservice.mapper;

import dev.iot.eventservice.model.Measurement;
import dev.iot.shared.dto.CreateMeasurementDto;
import dev.iot.shared.dto.MeasurementDto;
import dev.iot.shared.utils.Units;
import org.springframework.stereotype.Component;

@Component
public class MeasurementMapper {

    public Measurement toMeasurement(CreateMeasurementDto dto) {
        return new Measurement(
                dto.type(),
                dto.value(),
                Units.getUnit(dto.type())
        );
    }

    public MeasurementDto toMeasurementDto(Measurement measurement) {
        return new MeasurementDto(
                measurement.getType(),
                measurement.getValue(),
                measurement.getUnit()
        );
    }
}
