import request from '../utils/request'

/**
 * AI Todo 接口预留。
 * 当前页面先使用本地存储保证前端可演示；后端实现后，可直接接入这些接口。
 */
export const aiTodoApi = {
  list() {
    return request.get('/ai/todos')
  },
  create(data) {
    return request.post('/ai/todos', data)
  },
  update(id, data) {
    return request.put(`/ai/todos/${id}`, data)
  },
  remove(id) {
    return request.delete(`/ai/todos/${id}`)
  },
  run(id) {
    return request.post(`/ai/todos/${id}/run`)
  },
  steps(id) {
    return request.get(`/ai/todos/${id}/steps`)
  },
}
