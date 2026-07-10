package dev.iot.presenceservice.cache;

public enum DeviceType {
    CLIMATE("climate"),
    PRESENCE("presence"),
    LAMP("lamp");

    private final String type;

    DeviceType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
