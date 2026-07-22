package dev.iot.eventservice.service;

import io.grpc.StatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class YandexSyncService {

    private static final Logger logger = LoggerFactory.getLogger(YandexSyncService.class);

    private static final Map<Yandex.DeviceType, String> TYPE_BY_YANDEX = Map.of(
            Yandex.DeviceType.LAMP, "lamp"
    );

    private Optional<String> toDeviceType(Yandex.DeviceType yandexType) {
        return Optional.ofNullable(TYPE_BY_YANDEX.get(yandexType));
    }

    private final YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub;
    private final RoomService roomService;
    private final DeviceService deviceService;

    public YandexSyncService(
            YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub,
            RoomService roomService,
            DeviceService deviceService
    ) {
        this.yandexServiceStub = yandexServiceStub;
        this.roomService = roomService;
        this.deviceService = deviceService;
    }

    @Scheduled(fixedDelayString = "${app.yandex.sync-interval}", initialDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void scheduledSync() {
        try {
            sync();
        } catch (Exception e) {
            logger.error("Scheduled Yandex sync failed, next tick will retry", e);
        }
    }

    public void sync() throws StatusException {
        Yandex.ListDevicesRequest request = Yandex.ListDevicesRequest.newBuilder().build();
        Yandex.ListDevicesResponse devicesResponse = yandexServiceStub.listDevices(request);
        devicesResponse.getRoomsList().forEach(room -> {
            roomService.upsertFromSync(room.getExternalId(), room.getName());
        });
        devicesResponse.getDevicesList().forEach(device -> {
            Optional<String> deviceTypeOpt = toDeviceType(device.getType());
            if (deviceTypeOpt.isEmpty()) {
                return;
            }
            String deviceId = "lamp-" + device.getExternalId();
            String deviceType = deviceTypeOpt.get();
            String roomExternalId = device.getRoomExternalId();
            String roomId = roomExternalId.isBlank() ? null : "room-" + roomExternalId;
            String externalId = device.getExternalId();
            String externalKind = device.getKind().name();
            List<String> groupExternalIds = device.getGroupExternalIdsList();
            deviceService.upsertFromSync(deviceId, deviceType, roomId, externalId, externalKind, groupExternalIds);
        });
    }
}
