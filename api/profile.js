const { getPool, initDb } = require("./_db");

// GET /api/profile?userId=1
export default async function handler(req, res) {
  try {
    await initDb();
    const db = getPool();
    const userId = Number(req.query.userId);
    if (!userId) return res.status(400).json({ ok: false, error: "缺userId" });
    const r = await db.query("SELECT id, username, nickname, bio, is_official FROM users WHERE id=$1", [userId]);
    if (!r.rows.length) return res.status(404).json({ ok: false, error: "用户不存在" });
    const u = r.rows[0];
    res.json({ ok: true, user: u });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
