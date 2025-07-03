package com.hyundai.todo.command.service;

import com.hyundai.todo.dto.TodoEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private static final String TOPIC = "todo-events";
    private final KafkaTemplate<String, TodoEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, TodoEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEvent(TodoEvent event) {
        kafkaTemplate.send(TOPIC, String.valueOf(event.getId()), event);
        System.out.println("Event Sent: " + event);
    }
}