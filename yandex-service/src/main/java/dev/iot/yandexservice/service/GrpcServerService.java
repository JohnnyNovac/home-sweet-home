package dev.iot.yandexservice.service;

import dev.iot.yandexservice.dto.*;
import dev.iot.yandexservice.exception.YandexApiException;
import dev.iot.yandexservice.mapper.YandexDiscoveredDeviceMapper;
import dev.iot.yandexservice.mapper.YandexDiscoveredRoomMapper;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

import java.util.List;

@Service
public class GrpcServerService extends YandexServiceGrpc.YandexServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServerService.class);

    private final YandexRestClient client;
    private final YandexDiscoveredDeviceMapper discoveredDeviceMapper;
    private final YandexDiscoveredRoomMapper discoveredRoomMapper;

    public GrpcServerService(
            YandexRestClient client,
            YandexDiscoveredDeviceMapper discoveredDeviceMapper,
            YandexDiscoveredRoomMapper discoveredRoomMapper
    ) {
        this.client = client;
        this.discoveredDeviceMapper = discoveredDeviceMapper;
        this.discoveredRoomMapper = discoveredRoomMapper;
    }

    @Override
    public void setState(Yandex.SetStateRequest request, StreamObserver<Yandex.SetStateResponse> responseObserver) {
        String externalId = request.getExternalId();
        boolean on = request.getOn();
        Yandex.TargetKind kind = request.getKind();
        try {
            Capability capability = new Capability(
                    "devices.capabilities.on_off",
                    new State("on", on)
            );

            switch (kind) {
                case GROUP -> {
                    DeviceGroupActionResponse response = client.sendGroupAction(
                            new DeviceGroupActionRequest(List.of(capability)), externalId);
                    verifyOk(response.status(), response.requestId());
                }
                case DEVICE -> {
                    DeviceActionResponse response = client.sendDeviceAction(
                            new DeviceActionRequest(List.of(new DeviceAction(externalId, List.of(capability)))));
                    verifyOk(response.status(), response.requestId());
                }
                default -> throw new IllegalArgumentException("Unsupported target kind: " + kind);
            }

            logger.info("Yandex action applied (externalId={}, kind={}, on={})", externalId, kind, on);
            responseObserver.onNext(Yandex.SetStateResponse.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (YandexApiException e) {
            logger.error("Yandex API rejected the state command (externalId={}, kind={}, on={}, httpStatus={}, requestId={}): {}",
                    externalId, kind, on, e.getStatusCode(), e.getRequestId(), e.getMessage());
            responseObserver.onError(e);
        } catch (Exception e) {
            logger.error("setState failed (externalId={}, kind={}, on={})", externalId, kind, on, e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void listDevices(Yandex.ListDevicesRequest request, StreamObserver<Yandex.ListDevicesResponse> responseObserver) {
        try {
            UserInfoResponse userInfo = client.getUserInfo();
            verifyOk(userInfo.status(), userInfo.requestId());
            Yandex.ListDevicesResponse listDevicesResponse = Yandex.ListDevicesResponse.newBuilder()
                    .addAllDevices(discoveredDeviceMapper.toDiscoveredDevices(userInfo))
                    .addAllRooms(discoveredRoomMapper.toDiscoveredRooms(userInfo))
                    .build();
            responseObserver.onNext(listDevicesResponse);
            responseObserver.onCompleted();
        } catch (YandexApiException e) {
            logger.error("Yandex API rejected the device-list request (httpStatus={}, requestId={}): {}",
                    e.getStatusCode(), e.getRequestId(), e.getMessage());
            responseObserver.onError(e);
        } catch (Exception e) {
            logger.error("listDevices failed", e);
            responseObserver.onError(e);
        }
    }

    private void verifyOk(String status, String requestId) {
        if (!"ok".equals(status)) {
            throw new YandexApiException(200, requestId, "Yandex API returned status=" + status);
        }
    }
}