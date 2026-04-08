package dev.iot.yandexservice.service;

import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;
import yandex.Yandex;
import yandex.YandexServiceGrpc;

@Service
public class GrpcServerService extends YandexServiceGrpc.YandexServiceImplBase {

    @Override
    public void turnOnOffLamp(Yandex.TurnOnOffLampRequest request, StreamObserver<Yandex.TurnOnOffLampResponse> responseObserver) {
        //TODO: implement
    }

}
