package dev.iot.eventservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "rooms")
public class Room {

    @Id
    private String id;
    private String name;
    private String externalId;

    public Room() {
    }

    public Room(String name) {
        this.name = name;
    }

    public Room(String name, String externalId) {
        this.name = name;
        this.externalId = externalId;
    }

    public Room(String id, String name, String externalId) {
        this.id = id;
        this.name = name;
        this.externalId = externalId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExternalId() {
        return externalId;
    }
}
