package com.hyundai.todo.query.controller;

import com.hyundai.todo.query.document.TodoDocument;
import com.hyundai.todo.query.service.TodoQueryService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/todos")
@CrossOrigin(origins = "*")
public class TodoQueryController {
    private final TodoQueryService service;
    public TodoQueryController(TodoQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<TodoDocument> getAllTodos() {
        return service.findAllTodos();
    }
}