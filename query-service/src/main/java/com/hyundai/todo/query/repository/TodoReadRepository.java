package com.hyundai.todo.query.repository;

import com.hyundai.todo.query.document.TodoDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TodoReadRepository extends MongoRepository<TodoDocument, Long> {
}