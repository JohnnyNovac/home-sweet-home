package dev.iot.yandexservice.service;

import dev.iot.yandexservice.dto.Capability;
import dev.iot.yandexservice.dto.DeviceAction;
import dev.iot.yandexservice.dto.DeviceActionRequest;
import dev.iot.yandexservice.dto.DeviceActionResponse;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
import dev.iot.yandexservice.dto.State;
import dev.iot.yandexservice.exception.YandexApiException;
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

    public GrpcServerService(YandexRestClient client) {
        this.client = client;
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

    private void verifyOk(String status, String requestId) {
        if (!"ok".equals(status)) {
            throw new YandexApiException(200, requestId, "Yandex API returned status=" + status);
        }
    }
}