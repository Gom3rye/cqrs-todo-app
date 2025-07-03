package com.hyundai.todo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoEvent {
    private EventType type;
    private Long id;
    private String task;
    private boolean done;

    public enum EventType {
        CREATED, UPDATED, DELETED
    }
}