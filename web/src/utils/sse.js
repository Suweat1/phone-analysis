// src/utils/sse.js — SSE 包装，自动断线重连（EventSource 自带，但加层关闭/重订阅 API）
import config from '@/config'

export class AlertStream {
  constructor (onAlert) {
    this.onAlert = onAlert
    this.es = null
  }

  open () {
    this.close()
    this.es = new EventSource(config.sseUrl)
    this.es.addEventListener('alert', (ev) => {
      try {
        const msg = JSON.parse(ev.data)
        this.onAlert && this.onAlert(msg)
      } catch (e) { /* ignore */ }
    })
    this.es.onerror = () => {
      // EventSource 自动重连；这里仅作日志
      console.warn('SSE error, browser will auto-reconnect')
    }
  }

  close () {
    if (this.es) {
      this.es.close()
      this.es = null
    }
  }
}

export default AlertStream
