package com.hyundai.todo.command.repository;

import com.hyundai.todo.command.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, Long> {
}