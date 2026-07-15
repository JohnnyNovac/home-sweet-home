package dev.iot.yandexservice.service;

import dev.iot.yandexservice.config.YandexProperties;
import dev.iot.yandexservice.dto.Capability;
import dev.iot.yandexservice.dto.State;
import dev.iot.yandexservice.dto.DeviceGroupActionRequest;
import dev.iot.yandexservice.dto.DeviceGroupActionResponse;
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
                                    new State("on", request.getTurnOn())
                            )
                    )
            );

            DeviceGroupActionResponse response = client.sendGroupAction(turnOnOffRequest, yandexProperties.chandelierId());

            logger.info("Yandex group action response (turnOn={}): {}", request.getTurnOn(), response);

            if (!"ok".equals(response.status())) {
                throw new YandexApiException(200, response.requestId(), "Yandex API returned status=" + response.status());
            }

            responseObserver.onNext(
                    Yandex.TurnOnOffLampResponse.newBuilder()
                            .setStatus("OK")
                            .build()
            );
            responseObserver.onCompleted();

        } catch (YandexApiException e) {
            logger.error("Yandex API rejected the lamp command (turnOn={}, httpStatus={}, requestId={}): {}",
                    request.getTurnOn(), e.getStatusCode(), e.getRequestId(), e.getMessage());
            responseObserver.onError(e);
        } catch (Exception e) {
            logger.error("turnOnOffLamp failed (turnOn={})", request.getTurnOn(), e);
            responseObserver.onError(e);
        }
    }
}
