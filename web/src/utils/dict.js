// src/utils/dict.js — 字段中英映射（前端缓存层）
// 启动时调一次 api.dictAll() 拉全量；找不到时返回英文原名
import api from '@/api'

const state = {
  loaded: false,
  map: {}    // { en: { columnCn, layer, category } }
}

export async function loadDict () {
  if (state.loaded) return state.map
  try {
    const data = await api.dictAll()
    state.map = data || {}
    state.loaded = true
  } catch (e) {
    // 后端没起来时不阻塞看板，返回空映射
    state.map = {}
  }
  return state.map
}

export function cn (en) {
  if (!en) return ''
  const v = state.map[en]
  return v && v.columnCn ? v.columnCn : en
}

export default { loadDict, cn }
