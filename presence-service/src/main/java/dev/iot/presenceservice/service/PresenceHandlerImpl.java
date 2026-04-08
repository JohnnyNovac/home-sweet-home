package dev.iot.presenceservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iot.presenceservice.config.MeasurementsProperties;
import dev.iot.shared.dto.EventDTO;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import dev.iot.shared.utils.JsonDtoParser;
import yandex.Yandex;
import yandex.YandexServiceGrpc;


@Service
public class PresenceHandlerImpl implements PresenceHandler {

    private final ObjectMapper objectMapper;
    private final MeasurementsProperties measurementsProperties;
    private final YandexServiceGrpc.YandexServiceStub yandexServiceStub;

    public PresenceHandlerImpl(ObjectMapper objectMapper, YandexServiceGrpc.YandexServiceStub yandexServiceStub, MeasurementsProperties measurementsProperties) {
        this.objectMapper = objectMapper;
        this.yandexServiceStub = yandexServiceStub;
        this.measurementsProperties = measurementsProperties;
    }

    @Override
    public Mono<EventDTO> handleIncomingData(String jsonData) {
        return Mono.fromCallable(() -> {
                    validateJsonFormat(jsonData);
                    return jsonData;
                })
                .flatMap(data -> {
                    EventDTO eventDTO = JsonDtoParser.parseJson(jsonData);
                    return eventDTO.measurements().stream()
                            .filter(m -> m.type().equals(measurementsProperties.getLampState().getName()))
                            .findFirst()
                            .map(m ->
                                    turnOnOffLamp((Boolean) m.value())
                                            .thenReturn(eventDTO)
                            )
                            .orElse(Mono.just(eventDTO));
                });

    }

    private Mono<Yandex.TurnOnOffLampResponse> turnOnOffLamp(boolean turnOn) {
        Yandex.TurnOnOffLampRequest request = Yandex.TurnOnOffLampRequest.newBuilder()
                .setTurnOn(turnOn)
                .build();

        return Mono.create(sink ->
                yandexServiceStub.turnOnOffLamp(request, new StreamObserver<>() {
                    @Override
                    public void onNext(Yandex.TurnOnOffLampResponse value) {
                        sink.success(value); // получили ответ
                    }

                    @Override
                    public void onError(Throwable t) {
                        sink.error(t); // ошибка
                    }

                    @Override
                    public void onCompleted() {
                        // можно игнорировать
                    }
                })
        );
    }

    private void validateJsonFormat(String jsonData) {
        try {
            JsonNode root = objectMapper.readTree(jsonData);
            JsonNode measurements = root.path("measurements");

            if (!measurements.has(measurementsProperties.getRadarPresence().getName())
                || !measurements.has(measurementsProperties.getPirSensorPresence().getName())
                || !measurements.has(measurementsProperties.getLampState().getName())) {
                throw new IllegalArgumentException("NodeMCU requires radarPresence, pirSensorPresence and lampState measurements");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON format", e);
        }
    }
}
