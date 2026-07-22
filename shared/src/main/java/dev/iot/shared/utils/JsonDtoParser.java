package dev.iot.shared.utils;

import dev.iot.shared.dto.CreateMeasurementDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class JsonDtoParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<CreateMeasurementDto> parseMeasurements(String jsonData) {
        JsonNode root = objectMapper.readTree(jsonData);
        JsonNode measurementsNode = root.get("measurements");
        return extractMeasurements(measurementsNode);
    }

    private static List<CreateMeasurementDto> extractMeasurements(JsonNode measurementsNode) {
        List<CreateMeasurementDto> createMeasurementDtos = new ArrayList<>();

        measurementsNode.properties().forEach(entry -> {
            String type = entry.getKey();
            JsonNode valueNode = entry.getValue();

            Object value;
            if (valueNode.isBoolean()) {
                value = valueNode.booleanValue();
            } else if (valueNode.isNumber()) {
                value = valueNode.numberValue();
            } else if (valueNode.isString()) {
                value = valueNode.stringValue();
            } else if (valueNode.isNull()) {
                value = null;
            } else {
                value = valueNode.toString();
            }

            createMeasurementDtos.add(new CreateMeasurementDto(type, value));
        });

        return createMeasurementDtos;
    }
}