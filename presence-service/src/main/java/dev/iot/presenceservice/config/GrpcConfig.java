package dev.iot.presenceservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import yandex.YandexServiceGrpc;

@Configuration
public class GrpcConfig {

    @Bean
    public YandexServiceGrpc.YandexServiceStub yandexServiceStub(GrpcChannelFactory channels) {
        return YandexServiceGrpc.newStub(channels.createChannel("default"));
    }

}
