package dev.iot.eventservice.controller;


import dev.iot.eventservice.dto.*;
import dev.iot.eventservice.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDto create(@RequestBody @Valid CreateRoomDto dto) {
        return roomService.create(dto);
    }

    @GetMapping
    public PagedModel<RoomDto> getRooms(@RequestParam(required = false, defaultValue = "0") int pageNumber,
                                            @RequestParam(required = false, defaultValue = "20") int pageSize) {
        return new PagedModel<>(roomService.getRooms(pageNumber, pageSize));
    }

    @PutMapping("/{id}")
    public RoomDto update(@PathVariable String id, @RequestBody @Valid UpdateRoomDto dto) {
        return roomService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        roomService.delete(id);
    }
}
