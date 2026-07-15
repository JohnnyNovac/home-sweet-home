package dev.iot.yandexservice.dto;

import java.util.List;

public record UserInfoResponse(
        String requestId,
        String status,
        List<Room> rooms,
        List<Group> groups,
        List<Device> devices,
        List<Scenario> scenarios,
        List<Household> households
) {
}
