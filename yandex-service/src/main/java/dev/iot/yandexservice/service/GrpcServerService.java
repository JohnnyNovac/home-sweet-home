package dev.iot.yandexservice.service;

import dev.iot.yandexservice.config.YandexProperties;
import dev.iot.yandexservice.dto.Capability;
import dev.iot.yandexservice.dto.CapabilityState;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
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
    private final YandexProperties yandexProperties;

    public GrpcServerService(YandexRestClient client, YandexProperties yandexProperties) {
        this.client = client;
        this.yandexProperties = yandexProperties;
    }

    @Override
    public void turnOnOffLamp(Yandex.TurnOnOffLampRequest request, StreamObserver<Yandex.TurnOnOffLampResponse> responseObserver) {
        try {
            DeviceGroupActionRequest turnOnOffRequest = new DeviceGroupActionRequest(
                    List.of(
                            new Capability(
                                    "devices.capabilities.on_off",
                                    new CapabilityState("on", request.getTurnOn())
                            )
                    )
            );

            DeviceGroupActionResponse response = client.sendGroupAction(turnOnOffRequest, yandexProperties.getChandelierId());

            if (!"ok".equals(response.status())) {
                throw new RuntimeException("Yandex API failed: " + response.status());
            }

            responseObserver.onNext(
                    Yandex.TurnOnOffLampResponse.newBuilder()
                            .setStatus("OK")
                            .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("turnOnOffLamp failed (turnOn={})", request.getTurnOn(), e);
            responseObserver.onError(e);
        }
    }
}
