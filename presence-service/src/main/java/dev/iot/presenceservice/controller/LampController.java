package dev.iot.presenceservice.controller;

import dev.iot.presenceservice.dto.LampStateRequest;
import dev.iot.presenceservice.dto.LampStateResponse;
import dev.iot.presenceservice.dto.OffDelayRequest;
import dev.iot.presenceservice.dto.ThresholdRequest;
import dev.iot.presenceservice.service.LampService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/lamp")
public class LampController {

    private final LampService lampService;

    public LampController(LampService lampService) {
        this.lampService = lampService;
    }

    @GetMapping
    public LampStateResponse get() {
        return new LampStateResponse(lampService.isLampOn(), lampService.getIlluminanceThreshold(), lampService.getLampOffDelay());
    }

    @PutMapping("/threshold")
    public LampStateResponse setThreshold(@RequestBody @Valid ThresholdRequest request) {
        lampService.setIlluminanceThreshold(request.illuminanceThreshold());
        return get();
    }

    @PutMapping("/off-delay")
    public LampStateResponse setOffDelay(@RequestBody @Valid OffDelayRequest request) {
        lampService.setLampOffDelay(Duration.ofSeconds(request.offDelay()));
        return get();
    }

    @PostMapping("/state")
    public LampStateResponse setState(@RequestBody @Valid LampStateRequest request) {
        lampService.setLamp(request.roomId(), request.on());
        return get();
    }
}