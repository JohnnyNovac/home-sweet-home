package dev.iot.yandexservice.mapper;

import dev.iot.yandexservice.dto.UserInfoResponse;
import org.springframework.stereotype.Component;
import yandex.Yandex;

import java.util.List;
import java.util.stream.Stream;

@Component
public class YandexDiscoveredRoomMapper {

    public List<Yandex.DiscoveredRoom> toDiscoveredRooms(UserInfoResponse userInfo) {
        return userInfo.rooms().stream().map(room ->
                Yandex.DiscoveredRoom.newBuilder().setExternalId(room.id()).setName(room.name()).build()).toList();
    }
}
