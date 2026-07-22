package dev.iot.eventservice.service;

import dev.iot.eventservice.dto.CreateRoomDto;
import dev.iot.eventservice.dto.RoomDto;
import dev.iot.eventservice.dto.UpdateRoomDto;
import dev.iot.eventservice.exception.RoomNotFoundException;
import dev.iot.eventservice.mapper.RoomMapper;
import dev.iot.eventservice.model.Room;
import dev.iot.eventservice.repository.RoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;

    public RoomService(RoomRepository roomRepository, RoomMapper roomMapper) {
        this.roomRepository = roomRepository;
        this.roomMapper = roomMapper;
    }

    public RoomDto create(CreateRoomDto dto) {
        Room room = roomRepository.insert(roomMapper.toRoom(dto));
        return roomMapper.toRoomDto(room);
    }

    public Page<RoomDto> getRooms(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return roomRepository.findAll(pageable).map(roomMapper::toRoomDto);
    }

    public RoomDto update(String id, UpdateRoomDto dto) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RoomNotFoundException(id));

        room.setName(dto.name());

        Room updatedRoom = roomRepository.save(room);
        return roomMapper.toRoomDto(updatedRoom);
    }

    public void upsertFromSync(String externalId, String name) {
        String id = "room-" + externalId;
        roomRepository.save(new Room(id, name, externalId));
    }

    public void delete(String id) {
        roomRepository.delete(roomRepository.findById(id)
                .orElseThrow(() -> new RoomNotFoundException(id)));
    }
}
