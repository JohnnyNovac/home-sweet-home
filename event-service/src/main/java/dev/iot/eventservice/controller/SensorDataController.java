package dev.iot.eventservice.controller;

import dev.iot.eventservice.service.SensorDataService;
import dev.iot.shared.dto.CreateEventDto;
import dev.iot.shared.dto.EventDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sensor-data")
public class SensorDataController {

    private final SensorDataService sensorDataService;

    public SensorDataController(SensorDataService sensorDataService) {
        this.sensorDataService = sensorDataService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto create(@RequestBody CreateEventDto dto) {
        return sensorDataService.create(dto);
    }

    @GetMapping
    public List<EventDto> getSensorData(@RequestParam(required = false, defaultValue = "0") int pageNumber,
                                        @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return sensorDataService.getSensorData(pageNumber, pageSize);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll() {
        sensorDataService.deleteAll();
    }
}
