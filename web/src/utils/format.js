// src/utils/format.js — 通用数值格式化
export function fmtMoney (v) {
  if (v == null || isNaN(v)) return '-'
  const n = Number(v)
  if (Math.abs(n) >= 1e8) return (n / 1e8).toFixed(2) + ' 亿'
  if (Math.abs(n) >= 1e4) return (n / 1e4).toFixed(2) + ' 万'
  return n.toFixed(2)
}

export function fmtInt (v) {
  if (v == null || isNaN(v)) return '-'
  return Number(v).toLocaleString('zh-CN')
}

export function fmtPct (v, digits = 2) {
  if (v == null || isNaN(v)) return '-'
  return (Number(v) * 100).toFixed(digits) + '%'
}

export function fmtScore (v) {
  if (v == null || isNaN(v)) return '-'
  return Number(v).toFixed(3)
}

export default { fmtMoney, fmtInt, fmtPct, fmtScore }
