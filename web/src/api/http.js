// src/api/http.js — 统一 axios 实例
import axios from 'axios'
import { Message } from 'element-ui'
import config from '@/config'

const http = axios.create({
  baseURL: config.apiBase,
  timeout: 15000
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    // 后端统一 {code,msg,data}
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      Message.error(body.msg || '业务错误')
      return Promise.reject(new Error(body.msg || 'biz error'))
    }
    return body
  },
  (err) => {
    Message.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default http
