const { getPool, initDb } = require("./_db");

// POST /api/delete {userId} 注销账号
export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ ok: false, error: "只支持POST" });
  const { userId } = req.body || {};
  if (!userId) return res.status(400).json({ ok: false, error: "缺userId" });
  try {
    await initDb();
    const db = getPool();
    await db.query("DELETE FROM messages WHERE sender_id=$1 OR receiver_id=$1", [userId]);
    await db.query("DELETE FROM friendships WHERE user_id=$1 OR friend_id=$1", [userId]);
    await db.query("DELETE FROM users WHERE id=$1", [userId]);
    res.json({ ok: true });
  } catch (e) {
    res.status(500).json({ ok: false, error: "注销失败，请稍后再试" });
  }
}
