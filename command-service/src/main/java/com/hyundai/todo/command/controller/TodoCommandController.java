package com.hyundai.todo.command.controller;

import com.hyundai.todo.command.entity.Todo;
import com.hyundai.todo.command.service.TodoCommandService;
import com.hyundai.todo.dto.TodoEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/todos")
@CrossOrigin(origins = "*")
public class TodoCommandController {

    private final TodoCommandService service;

    public TodoCommandController(TodoCommandService service) {
        this.service = service;
    }

    // 프론트엔드에서 { task: "할 일" } 형태로 보내는 것을 가정
    @PostMapping
    public Todo create(@RequestBody Map<String, String> payload) {
        return service.createTodo(payload.get("task"));
    }

    @PutMapping("/{id}")
    public Todo update(@PathVariable Long id, @RequestBody Todo todo) {
        return service.updateTodo(id, todo.getTask(), todo.isDone());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteTodo(id);
        return ResponseEntity.noContent().build();
    }
}