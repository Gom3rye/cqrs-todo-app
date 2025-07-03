package com.hyundai.todo.query.service;

import com.hyundai.todo.dto.TodoEvent;
import com.hyundai.todo.query.document.TodoDocument;
import com.hyundai.todo.query.repository.TodoReadRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private final TodoReadRepository repository;

    public KafkaConsumerService(TodoReadRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "todo-events", groupId = "todo-group")
    public void consumeEvent(TodoEvent event) {
        System.out.println("Event Received: " + event);
        switch (event.getType()) {
            case CREATED, UPDATED:
                TodoDocument doc = new TodoDocument();
                doc.setId(event.getId());
                doc.setTask(event.getTask());
                doc.setDone(event.isDone());
                repository.save(doc);
                break;
            case DELETED:
                repository.deleteById(event.getId());
                break;
        }
    }
}