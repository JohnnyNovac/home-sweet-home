package dev.iot.eventservice.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SensorHandlerFactory {

    private final Map<String, SensorHandler> handlers;

    public SensorHandlerFactory(List<SensorHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(SensorHandler::getType, h -> h));
    }

    public SensorHandler getHandler(String type) {
        return handlers.get(type);
    }

    public List<SensorHandler> getHandlers() {
        return handlers.values().stream().toList();
    }
}
