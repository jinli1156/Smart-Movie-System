/**
 * 兼容旧座位矩阵与含过道的新布局；数据非法时禁用全部座位，避免错误售票。
 */
export interface HallSeatLayout {
  seats: number[][]
  aisleAfterCols: number[]
  valid: boolean
}

/** 解析新旧布局并校验行列与过道；非法布局返回禁用座位。 */
export function parseHallSeatLayout(json: string | undefined, rows: number, cols: number): HallSeatLayout {
  try {
    const value = json?.trim() ? JSON.parse(json) : null
    const legacy = value === null || Array.isArray(value)
    const seats = legacy ? (value ?? Array.from({ length: rows }, () => Array(cols).fill(1))) : value.seats
    const aisles = legacy ? (cols > 1 ? [Math.floor(cols / 2)] : []) : value.aisleAfterCols
    if (!Array.isArray(seats) || seats.length !== rows || !seats.every(row =>
      Array.isArray(row) && row.length === cols && row.every(cell => cell === 0 || cell === 1)) ||
      !Array.isArray(aisles) || !aisles.every(col => Number.isInteger(col) && col >= 0 && col <= cols) ||
      new Set(aisles).size !== aisles.length) throw new Error('Invalid hall layout')
    return { seats, aisleAfterCols: aisles, valid: true }
  } catch {
    return { seats: Array.from({ length: rows }, () => Array(cols).fill(0)), aisleAfterCols: [], valid: false }
  }
}
