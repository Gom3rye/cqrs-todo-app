// src/api/todoApi.js
import axios from 'axios';
// ✅ 읽기(GET) 요청을 위한 주소
const READ_API_URL = '/api/query/todos';
// ✅ 쓰기(POST, PUT, DELETE) 요청을 위한 주소
const WRITE_API_URL = '/api/command/todos';

// const API_URL = '/api/todos';

export const getTodos = async () => {
  try {
    const response = await axios.get(READ_API_URL);
    console.log(response.data);
    return response.data;
  } catch (error) {
    console.error('Error fetching todos:', error);
    throw error;
  }
};

export const createTodo = async (task) => {
  try {
    const response = await axios.post(WRITE_API_URL, { task });
    return response.data;
  } catch (error) {
    console.error('Error creating todo:', error);
    throw error;
  }
};

export const updateTodo = async (id, updatedTodo) => {
  try {
    const response = await axios.put(`${WRITE_API_URL}/${id}`, updatedTodo);
    return response.data;
  } catch (error) {
    console.error('Error updating todo:', error);
    throw error;
  }
};

export const deleteTodo = async (id) => {
  try {
    await axios.delete(`${WRITE_API_URL}/${id}`);
  } catch (error) {
    console.error('Error deleting todo:', error);
    throw error;
  }
};

export const toggleTodoDone = async (id, updatedTodo) => {
  try {
    const res = await axios.put(`${WRITE_API_URL}/${id}`, updatedTodo);
    return res.data;
  } catch (error) {
    console.error('Error toggling todo done:', error);
    throw error;
  }
};
