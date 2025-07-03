import React, { useState, useEffect } from 'react';
import { getTodos, createTodo, deleteTodo, toggleTodoDone } from '../api/todoApi';
import './TodoList.css'; // 👈 스타일 추가

const TodoList = () => {
  const [todos, setTodos] = useState([]);
  const [newTask, setNewTask] = useState('');

  useEffect(() => {
    const fetchTodos = async () => {
      try {
        const data = await getTodos();
        console.log("불러온 할 일:", data);
        setTodos(data);
      } catch (error) {
        console.error('Error fetching todos:', error);
      }
    };
    fetchTodos();
  }, []);

  const handleAddTodo = async () => {
    if (!newTask.trim()) return;
    try {
      const addedTodo = await createTodo(newTask);
      setTodos([...todos, addedTodo]);
      setNewTask('');
    } catch (error) {
      console.error('Error adding todo:', error);
    }
  };

  const handleDeleteTodo = async (id) => {
    try {
      await deleteTodo(id);
      setTodos(todos.filter(todo => todo.id !== id));
    } catch (error) {
      console.error('Error deleting todo:', error);
    }
  };

  const handleToggleDone = async (todo) => {
    try {
      const updated = { ...todo, done: !todo.done };
      const updatedTodo = await toggleTodoDone(todo.id, updated);
      setTodos(todos.map(t => (t.id === todo.id ? updatedTodo : t)));
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="todo-container">
      <h2>📝 할 일 목록</h2>
      <div className="todo-input">
        <input
          type="text"
          value={newTask}
          onChange={(e) => setNewTask(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              handleAddTodo();
            }
          }}
          placeholder="새 할 일을 입력하세요."
        />
        <button onClick={handleAddTodo}>추가</button>
      </div>

      <ul className="todo-list">
        {todos.map(todo => (
          <li key={todo.id} className={todo.done ? 'done' : ''}>
            <label>
              <input
                type="checkbox"
                checked={todo.done}
                onChange={() => handleToggleDone(todo)}
              />
              <span>{todo.task}</span>
            </label>
            <button className="delete-btn" onClick={() => handleDeleteTodo(todo.id)}>삭제</button>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default TodoList;