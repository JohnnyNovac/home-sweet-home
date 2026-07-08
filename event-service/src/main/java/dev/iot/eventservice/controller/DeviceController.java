package dev.iot.eventservice.controller;

import dev.iot.eventservice.dto.CreateDeviceDto;
import dev.iot.eventservice.dto.DeviceDto;
import dev.iot.eventservice.dto.UpdateDeviceDto;
import dev.iot.eventservice.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceDto create(@RequestBody @Valid CreateDeviceDto dto) {
        return deviceService.create(dto);
    }

    @GetMapping
    public PagedModel<DeviceDto> getDevices(@RequestParam(required = false, defaultValue = "0") int pageNumber,
                                            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return new PagedModel<>(deviceService.getDevices(pageNumber, pageSize));
    }

    @PutMapping("/{id}")
    public DeviceDto update(@PathVariable String id, @RequestBody UpdateDeviceDto dto) {
        return deviceService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        deviceService.delete(id);
    }
}
