const { getPool, initDb } = require("./_db");

// GET /api/friends?userId=1  好友列表
// POST /api/friends {userId, friendId}  加好友（双向）
export default async function handler(req, res) {
  try {
    await initDb();
    const db = getPool();
    if (req.method === "GET") {
      const userId = Number(req.query.userId);
      if (!userId) return res.status(400).json({ ok: false, error: "缺userId" });
      const r = await db.query(
        `SELECT u.id, u.username, u.nickname, u.bio, u.is_official FROM friendships f
         JOIN users u ON u.id = (CASE WHEN f.user_id=$1 THEN f.friend_id ELSE f.user_id END)
         WHERE f.user_id=$1 OR f.friend_id=$1 ORDER BY u.id`,
        [userId]
      );
      return res.json({ ok: true, friends: r.rows });
    }
    if (req.method === "POST") {
      const { userId, friendId } = req.body || {};
      if (!userId || !friendId || userId === friendId)
        return res.status(400).json({ ok: false, error: "参数不对" });
      const u = await db.query("SELECT id FROM users WHERE id IN ($1,$2)", [userId, friendId]);
      if (u.rows.length < 2) return res.status(400).json({ ok: false, error: "用户不存在" });
      await db.query(
        `INSERT INTO friendships (user_id, friend_id)
         SELECT LEAST($1,$2), GREATEST($1,$2)
         WHERE NOT EXISTS (
           SELECT 1 FROM friendships
           WHERE (user_id=$1 AND friend_id=$2) OR (user_id=$2 AND friend_id=$1)
         )`,
        [userId, friendId]
      );
      return res.json({ ok: true });
    }
    res.status(405).json({ ok: false, error: "只支持GET/POST" });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
