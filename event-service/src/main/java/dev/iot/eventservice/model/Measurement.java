package dev.iot.eventservice.model;

public class Measurement {

    private String type;
    private Object value;
    private String unit;

    public Measurement(String type, Object value, String unit) {
        this.type = type;
        this.value = value;
        this.unit = unit;
    }

    public String getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

}
