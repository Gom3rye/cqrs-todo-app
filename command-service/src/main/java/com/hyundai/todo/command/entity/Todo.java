package com.hyundai.todo.command.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Todo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String task;
    private boolean done;
}