package dev.iot.eventservice.repository;

import dev.iot.eventservice.model.SensorData;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorDataRepository extends ReactiveMongoRepository<SensorData, String> {
}
