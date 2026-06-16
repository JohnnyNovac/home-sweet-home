package dev.iot.eventservice.service;

/**
 * Handler for a single sensor type ({@code climate}, {@code presence}, …).
 * <p>
 * Implementations are registered as Spring beans and selected by {@link SensorHandlerFactory}
 * by the value of {@link #getType()}, which {@link EventRunner} extracts from the routing key of the
 * incoming message. To add a new sensor type, just create another bean implementing this interface —
 * the dispatch does not need to change.
 */
public interface SensorHandler {

    /**
     * Handles one incoming sensor data message: validates the payload, reflects the state in Home
     * Assistant (and, the first time a device appears, publishes its discovery config) and stores the
     * measurements.
     *
     * @param deviceId device identifier from the routing key (e.g. {@code esp01})
     * @param jsonData measurements JSON, e.g. {@code {"measurements": {"temperature": 22.5, "humidity": 55}}}
     * @throws IllegalArgumentException if the payload is missing measurements required for this type
     */
    void handleIncomingData(String deviceId, String jsonData);

    /**
     * Re-publishes discovery configs to Home Assistant for every device this handler has already seen.
     * {@link EventRunner} calls this when Home Assistant reports it has come online, to restore the
     * entities after its restart.
     */
    void sendDiscoveryForAll();

    /**
     * @return the sensor type served by this handler; must match the {@code sensorType} segment of the
     *         routing key ({@code home.<sensorType>.<deviceId>.data}) — {@link SensorHandlerFactory}
     *         picks the handler by it
     */
    String getType();
}