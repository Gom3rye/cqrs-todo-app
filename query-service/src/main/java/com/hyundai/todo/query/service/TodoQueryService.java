package com.hyundai.todo.query.service;

import com.hyundai.todo.query.document.TodoDocument;
import com.hyundai.todo.query.repository.TodoReadRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TodoQueryService {
    private final TodoReadRepository repository;
    public TodoQueryService(TodoReadRepository repository) {
        this.repository = repository;
    }
    public List<TodoDocument> findAllTodos() {
        return repository.findAll();
    }
}