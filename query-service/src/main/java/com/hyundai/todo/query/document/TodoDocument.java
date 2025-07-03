package com.hyundai.todo.query.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "todos")
public class TodoDocument {
    @Id
    private Long id; // MySQL의 ID를 그대로 사용
    private String task;
    private boolean done;
}