package dev.iot.presenceservice.controller;

import dev.iot.presenceservice.dto.LampStateRequest;
import dev.iot.presenceservice.dto.LampStateResponse;
import dev.iot.presenceservice.dto.ThresholdRequest;
import dev.iot.presenceservice.service.LampService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/lamp")
public class LampController {

    private final LampService lampService;

    public LampController(LampService lampService) {
        this.lampService = lampService;
    }

    @GetMapping
    public LampStateResponse get() {
        return new LampStateResponse(lampService.isLampOn(), lampService.getIlluminanceThreshold());
    }

    @PutMapping("/threshold")
    public LampStateResponse setThreshold(@RequestBody ThresholdRequest request) {
        if (request.illuminanceThreshold() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "illuminanceThreshold must be >= 0");
        }
        lampService.setIlluminanceThreshold(request.illuminanceThreshold());
        return get();
    }

    @PostMapping("/state")
    public LampStateResponse setState(@RequestBody LampStateRequest request) {
        lampService.setLamp(request.on());
        return get();
    }
}