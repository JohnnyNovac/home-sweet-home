package utils;

import dev.iot.shared.dto.MeasurementDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class JsonDtoParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String parseSensorId(String jsonData) {
        JsonNode root = objectMapper.readTree(jsonData);
        return root.get("sensorId").asString();
    }

    public static List<MeasurementDTO> parseMeasurements(String jsonData) {
        List<MeasurementDTO> measurementDTOs = new ArrayList<>();

        JsonNode root = objectMapper.readTree(jsonData);
        JsonNode measurementsNode = root.get("measurements");

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
            } else {
                value = valueNode.toString();
            }

            measurementDTOs.add(new MeasurementDTO(type, value));
        });

        return measurementDTOs;
    }


}
