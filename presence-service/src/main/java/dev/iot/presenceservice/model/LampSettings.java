package dev.iot.presenceservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "settings")
public record LampSettings(@Id String id, double illuminanceThreshold) {
}