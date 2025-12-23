package dev.iot.eventservice.mapper;

import dev.iot.eventservice.model.Measurement;
import dev.iot.shared.dto.MeasurementDTO;
import org.springframework.stereotype.Component;
import dev.iot.shared.utils.Units;

@Component
public class MeasurementMapper {

    public Measurement toEntity(MeasurementDTO dto) {
        return new Measurement(
                dto.type(),
                dto.value(),
                Units.getUnit(dto.type())
        );
    }

}
