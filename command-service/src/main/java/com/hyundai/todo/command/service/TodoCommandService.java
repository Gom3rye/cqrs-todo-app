package com.hyundai.todo.command.service;

import com.hyundai.todo.command.entity.Todo;
import com.hyundai.todo.command.repository.TodoRepository;
import com.hyundai.todo.dto.TodoEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoCommandService {

    private final TodoRepository todoRepository;
    private final KafkaProducerService kafkaProducerService;

    public TodoCommandService(TodoRepository todoRepository, KafkaProducerService kafkaProducerService) {
        this.todoRepository = todoRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Transactional
    public Todo createTodo(String task) {
        Todo todo = new Todo();
        todo.setTask(task);
        todo.setDone(false);
        Todo savedTodo = todoRepository.save(todo);

        TodoEvent event = new TodoEvent(TodoEvent.EventType.CREATED, savedTodo.getId(), savedTodo.getTask(), savedTodo.isDone());
        kafkaProducerService.sendEvent(event);
        return savedTodo;
    }

    @Transactional
    public Todo updateTodo(Long id, String task, boolean done) {
        Todo todo = todoRepository.findById(id).orElseThrow(() -> new RuntimeException("Todo not found"));
        todo.setTask(task);
        todo.setDone(done);
        Todo updatedTodo = todoRepository.save(todo);

        TodoEvent event = new TodoEvent(TodoEvent.EventType.UPDATED, updatedTodo.getId(), updatedTodo.getTask(), updatedTodo.isDone());
        kafkaProducerService.sendEvent(event);
        return updatedTodo;
    }

    @Transactional
    public void deleteTodo(Long id) {
        todoRepository.deleteById(id);
        TodoEvent event = new TodoEvent(TodoEvent.EventType.DELETED, id, null, false);
        kafkaProducerService.sendEvent(event);
    }
}