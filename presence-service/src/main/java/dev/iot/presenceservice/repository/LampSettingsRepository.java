package dev.iot.presenceservice.repository;

import dev.iot.presenceservice.model.LampSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LampSettingsRepository extends MongoRepository<LampSettings, String> {
}