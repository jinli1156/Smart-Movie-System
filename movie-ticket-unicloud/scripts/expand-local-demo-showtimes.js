// 本机 MySQL 演示数据补充工具，与 uniCloud 数据无关；依赖同目录的 mysql2。
// 先预览：node expand-local-demo-showtimes.js --from=2026-10-20 --to=2026-10-25
// 写入时追加 --apply。数据库密码从 DB_PASSWORD 读取，不保存到输出文件。
const mysql = require('mysql2/promise')
const fs = require('node:fs')
const path = require('node:path')

const args = process.argv.slice(2)
const option = (name, fallback) => args.find(value => value.startsWith(`--${name}=`))?.split('=')[1] || fallback
const from = option('from', '2026-10-20')
const to = option('to', '2026-10-25')
const apply = args.includes('--apply')
const output = path.resolve(option('output', path.join(__dirname, '../../.local/defense-showtimes-20261019')))
const minute = value => Number(value.slice(11, 13)) * 60 + Number(value.slice(14, 16))
const timestamp = (day, value) => `${day} ${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}:00`
const standardClocks = new Set(['09:40:00', '13:20:00', '17:00:00', '20:40:00'])

async function main() {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to) || from > to) throw Error('日期范围无效')
  const db = await mysql.createConnection({
    host: process.env.DB_HOST || '127.0.0.1', port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USERNAME || 'root', password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME || 'movie_ticket', dateStrings: true, charset: 'utf8mb4'
  })
  try {
    await db.beginTransaction()
    // 与应用的手工排片持有相同影厅锁，再锁场次，避免覆盖并发售票或编辑。
    await db.query('SELECT id FROM hall WHERE deleted=0 ORDER BY cinema_id,id FOR UPDATE')
    const [[clock]] = await db.query("SELECT DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s') now, DATE_FORMAT(installed_on,'%Y-%m-%d %H:%i:%s') seeded_at FROM flyway_schema_history WHERE version='22' AND success=1")
    if (!clock) throw Error('请先完成 V22 初始演示排片迁移')
    const [all] = await db.query(`SELECT * FROM showtime WHERE deleted=0
      AND start_time>=DATE_SUB(?,INTERVAL 1 DAY) AND start_time<DATE_ADD(?,INTERVAL 2 DAY)
      ORDER BY hall_id,start_time FOR UPDATE`, [from, to])
    const [movies] = await db.query('SELECT id,duration FROM movie WHERE deleted=0 AND status=1')
    const durations = new Map(movies.map(movie => [movie.id, movie.duration]))
    const [halls] = await db.query(`SELECT h.id,h.cinema_id,h.name,h.total_seats FROM hall h JOIN cinema c ON c.id=h.cinema_id
      WHERE h.deleted=0 AND h.status=1 AND c.deleted=0 AND c.status=1 AND h.total_seats=h.total_rows*h.total_cols`)
    const hallMap = new Map(halls.map(hall => [hall.id, hall]))
    const [orders] = await db.query('SELECT DISTINCT showtime_id FROM ticket_order')
    const ordered = new Set(orders.map(order => order.showtime_id))
    const groups = new Map()
    for (const row of all) {
      const day = row.start_time.slice(0, 10)
      if (day < from || day > to) continue
      const key = `${day}/${row.hall_id}`
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key).push(row)
    }
    const plan = []
    for (const rows of groups.values()) {
      const day = rows[0].start_time.slice(0, 10)
      const hall = hallMap.get(rows[0].hall_id)
      // 仅整理 V22 生成、尚未编辑/售票/下单/开场的完整四场。
      // 手动新增、取消、已有任意状态订单的影厅当日排片都原样保留。
      if (!hall || rows.length !== 4 || new Set(rows.map(row => row.movie_id)).size !== 4) continue
      if (rows.some(row => row.create_time !== clock.seeded_at || row.update_time !== row.create_time ||
        !standardClocks.has(row.start_time.slice(11)) || row.status !== 0 || row.available_seats !== row.total_seats ||
        ordered.has(row.id) || row.start_time <= clock.now || !durations.has(row.movie_id))) continue
      const shortest = [...rows].sort((a, b) => durations.get(a.movie_id) - durations.get(b.movie_id))
      const startMinute = 9 * 60 + (hall.id % 3) * 10
      let sessions
      for (const extraCount of [2, 1]) {
        const repeats = shortest.slice(0, extraCount)
        // 避免最后一场原排片与补充场次连续放同一影片。
        if (repeats[0].movie_id === rows[3].movie_id) {
          if (extraCount === 2) repeats.reverse()
        }
        let sequence = [...rows, ...repeats.map(row => ({...row, id: null}))]
        if (extraCount === 1 && repeats[0].movie_id === rows[3].movie_id) {
          sequence = [rows[0], rows[1], {...repeats[0], id: null}, rows[2], rows[3]]
        }
        let cursor = startMinute
        sessions = sequence.map(row => {
          const start = cursor
          const end = start + durations.get(row.movie_id)
          cursor = Math.ceil((end + 20) / 5) * 5
          return {id: row.id, movie_id: row.movie_id, cinema_id: hall.cinema_id, hall_id: hall.id,
            start_time: timestamp(day, start), end_time: timestamp(day, end), base_price: row.base_price,
            total_seats: hall.total_seats, available_seats: hall.total_seats, status: 0, endMinute: end}
        })
        if (sessions.at(-1).endMinute <= 24 * 60) break
        sessions = null
      }
      if (!sessions || sessions[0].start_time <= clock.now) continue
      const ids = new Set(rows.map(row => row.id))
      if (sessions.some(candidate => all.some(existing => !ids.has(existing.id) && existing.hall_id === hall.id &&
        Date.parse(candidate.start_time.replace(' ', 'T') + '+08:00') < Date.parse(existing.end_time.replace(' ', 'T') + '+08:00') + 20 * 60000 &&
        Date.parse(candidate.end_time.replace(' ', 'T') + '+08:00') + 20 * 60000 > Date.parse(existing.start_time.replace(' ', 'T') + '+08:00')))) continue
      plan.push({day, hallId: hall.id, before: rows, after: sessions})
    }
    const summary = {from, to, apply, committed: false, hallDays: plan.length,
      updated: plan.reduce((n, day) => n + day.before.length, 0),
      inserted: plan.reduce((n, day) => n + day.after.length - day.before.length, 0)}
    if (!plan.length) {
      await db.rollback()
      console.log(JSON.stringify(summary))
      return
    }
    fs.mkdirSync(output, {recursive: true})
    fs.writeFileSync(path.join(output, apply ? 'applied-plan.json' : 'preview-plan.json'), JSON.stringify({summary, plan}, null, 2))
    if (apply) {
      for (const day of plan) for (const row of day.after) {
        if (row.id) {
          await db.execute('UPDATE showtime SET start_time=?,end_time=? WHERE id=?', [row.start_time, row.end_time, row.id])
        } else {
          const hour = minute(row.start_time) / 60
          const hall = hallMap.get(row.hall_id)
          const weekend = [0, 6].includes(new Date(`${day.day}T12:00:00+08:00`).getUTCDay())
          const premium = /VIP/i.test(hall.name) ? 12 : /IMAX|巨幕/i.test(hall.name) ? 10 : /杜比|激光/.test(hall.name) ? 6 : 0
          row.base_price = (hour < 12 ? 32 : hour < 17 ? 39 : hour < 20 ? 45 : 49) + premium + (weekend ? 3 : 0) + hall.cinema_id % 3 * 2
          const [result] = await db.execute(`INSERT INTO showtime
            (movie_id,cinema_id,hall_id,start_time,end_time,base_price,total_seats,available_seats,status)
            VALUES (?,?,?,?,?,?,?,?,0)`, [row.movie_id,row.cinema_id,row.hall_id,row.start_time,row.end_time,row.base_price,row.total_seats,row.available_seats])
          row.id = result.insertId
        }
      }
      const [[audit]] = await db.query(`SELECT COUNT(*) conflicts FROM showtime a JOIN showtime b
        ON a.hall_id=b.hall_id AND a.id<b.id AND b.deleted=0
        WHERE a.deleted=0 AND a.start_time>=? AND a.start_time<DATE_ADD(?,INTERVAL 1 DAY)
          AND a.start_time<DATE_ADD(b.end_time,INTERVAL 20 MINUTE)
          AND DATE_ADD(a.end_time,INTERVAL 20 MINUTE)>b.start_time`, [from, to])
      if (audit.conflicts) throw Error(`发现 ${audit.conflicts} 组排片冲突，本次写入将回滚`)
      await db.commit()
      summary.committed = true
      fs.writeFileSync(path.join(output, 'applied-plan.json'), JSON.stringify({summary, plan}, null, 2))
    } else await db.rollback()
    console.log(JSON.stringify(summary))
  } catch (error) {
    await db.rollback()
    throw error
  } finally { await db.end() }
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
