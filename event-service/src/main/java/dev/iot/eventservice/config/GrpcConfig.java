package dev.iot.eventservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import yandex.YandexServiceGrpc;

@Configuration
public class GrpcConfig {

    @Bean
    public YandexServiceGrpc.YandexServiceBlockingV2Stub yandexServiceStub(GrpcChannelFactory channels) {
        return YandexServiceGrpc.newBlockingV2Stub(channels.createChannel("yandex-service"));
    }
}
