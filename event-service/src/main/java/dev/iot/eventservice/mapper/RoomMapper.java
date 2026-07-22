package dev.iot.eventservice.mapper;

import dev.iot.eventservice.dto.CreateRoomDto;
import dev.iot.eventservice.dto.RoomDto;
import dev.iot.eventservice.model.Room;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {

    public Room toRoom(CreateRoomDto createRoomDto) {
        return new Room(createRoomDto.name());
    }

    public RoomDto toRoomDto(Room room) {
        return new RoomDto(
                room.getId(),
                room.getName(),
                room.getExternalId()
        );
    }
}
