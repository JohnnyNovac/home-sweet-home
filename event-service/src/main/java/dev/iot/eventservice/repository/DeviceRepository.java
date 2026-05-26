package dev.iot.eventservice.repository;

import dev.iot.eventservice.model.Device;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends ReactiveMongoRepository<Device, String> {
}