const { getPool, initDb } = require("./_db");

// GET /api/users?q=xxx&me=1  搜索用户
export default async function handler(req, res) {
  try {
    await initDb();
    const db = getPool();
    const q = (req.query.q || "").trim();
    const me = Number(req.query.me || 0);
    if (!q) return res.json({ ok: true, users: [] });
    const r = await db.query(
      "SELECT id, username, nickname FROM users WHERE (username ILIKE $1 OR nickname ILIKE $1) AND id <> $2 LIMIT 10",
      ["%" + q + "%", me]
    );
    res.json({ ok: true, users: r.rows });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
