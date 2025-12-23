package dev.iot.presenceservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.shared.dto.EventDTO;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import dev.iot.shared.utils.JsonDtoParser;

@Service
public class PresenceHandlerImpl implements PresenceHandler {

    private final ObjectMapper objectMapper;

    public PresenceHandlerImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<EventDTO> handleIncomingData(String jsonData) {
        return Mono.fromCallable(() -> {
                    validateJsonFormat(jsonData);
                    return jsonData;
                })
                .flatMap(data -> {
                    EventDTO eventDTO = JsonDtoParser.parseJson(jsonData);
                    return Mono.just(eventDTO);
                });

    }

    private void validateJsonFormat(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode measurements = root.path("measurements");

            if (!measurements.has("radarPresence") || !measurements.has("pirSensorPresence") || !measurements.has("lampState")) {
                throw new IllegalArgumentException("NodeMCU requires radarPresence, pirSensorPresence and lampState measurements");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }
}
