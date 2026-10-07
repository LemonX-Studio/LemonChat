const { getPool, initDb } = require("./_db");

// GET /api/admin?userId=1  仅官方号可用
export default async function handler(req, res) {
  try {
    await initDb();
    const db = getPool();
    const userId = Number(req.query.userId);
    if (!userId) return res.status(400).json({ ok: false, error: "缺userId" });
    const me = await db.query("SELECT is_official FROM users WHERE id=$1", [userId]);
    if (!me.rows.length || !me.rows[0].is_official)
      return res.status(403).json({ ok: false, error: "无权限" });
    const r = await db.query(
      `SELECT id, username, nickname, pass_hash, reg_ip, reg_ua, last_seen, created_at,
              (last_seen > now() - interval '5 minutes') AS online
       FROM users ORDER BY id ASC`
    );
    const online = r.rows.filter(u => u.online).length;
    res.json({ ok: true, total: r.rows.length, online, users: r.rows });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
