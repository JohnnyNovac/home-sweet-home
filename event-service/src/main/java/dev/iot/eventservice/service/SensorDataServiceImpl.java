package dev.iot.eventservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.eventservice.model.Measurement;
import dev.iot.eventservice.model.SensorData;
import dev.iot.eventservice.repository.SensorDataRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@Service
public class SensorDataServiceImpl implements SensorDataService {

    private final SensorDataRepository repository;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> UNIT_MAP = Map.of(
            "temperature", "°C",
            "humidity", "%"
    );

    public SensorDataServiceImpl(SensorDataRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<SensorData> handleIncomingData(String sensorId, String jsonData) {
        try {
            List<Measurement> measurements = parseJsonToMeasurements(jsonData);
            SensorData data = new SensorData(sensorId, Instant.now(), measurements);
            return repository.save(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Measurement> parseJsonToMeasurements(String jsonData) throws Exception {
        List<Measurement> measurements = new ArrayList<>();

        JsonNode root = objectMapper.readTree(jsonData);

        root.properties().forEach(entry -> {
            String type = entry.getKey();
            JsonNode valueNode = entry.getValue();

            Object value;
            if (valueNode.isBoolean()) {
                value = valueNode.booleanValue();
            } else if (valueNode.isNumber()) {
                value = valueNode.numberValue();
            } else if (valueNode.isTextual()) {
                value = valueNode.textValue();
            } else {
                value = valueNode.toString();
            }

            String unit = UNIT_MAP.getOrDefault(type, "unknown");

            measurements.add(new Measurement(type, value, unit));
        });

        return measurements;
    }

}
