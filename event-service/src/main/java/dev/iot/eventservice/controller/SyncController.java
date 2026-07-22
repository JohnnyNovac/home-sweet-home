package dev.iot.eventservice.controller;

import dev.iot.eventservice.service.YandexSyncService;
import io.grpc.StatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncController {

    private final YandexSyncService yandexSyncService;

    public SyncController(YandexSyncService yandexSyncService) {
        this.yandexSyncService = yandexSyncService;
    }

    @PostMapping("/api/v1/devices/sync")
    public void sync() throws StatusException {
        yandexSyncService.sync();
    }
}
