package dev.iot.presenceservice.service;

import dev.iot.shared.dto.EventDTO;
import dev.iot.shared.dto.MeasurementDTO;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import utils.JsonDtoParser;

import java.util.List;

@Service
public class PresenceHandlerImpl implements PresenceHandler {

    @Override
    public Mono<EventDTO> handleIncomingData(String sensorId, String jsonData) {
        List<MeasurementDTO> measurementDTOs = JsonDtoParser.parseMeasurements(jsonData);
        EventDTO eventDTO = new EventDTO(sensorId, measurementDTOs);
        return Mono.just(eventDTO);
    }
}
