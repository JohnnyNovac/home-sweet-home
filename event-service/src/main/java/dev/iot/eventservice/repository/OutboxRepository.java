package dev.iot.eventservice.repository;

import dev.iot.eventservice.model.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends MongoRepository<OutboxEvent,String> {

    List<OutboxEvent> findBySentFalseOrderByCreatedAt();
}
