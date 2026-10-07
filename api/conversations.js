const { getPool, initDb } = require("./_db");

// GET /api/conversations?userId=1  会话列表（好友+最后一条消息）
export default async function handler(req, res) {
  try {
    await initDb();
    const db = getPool();
    const userId = Number(req.query.userId);
    if (!userId) return res.status(400).json({ ok: false, error: "缺userId" });
    const friends = await db.query(
      `SELECT u.id, u.username, u.nickname FROM friendships f
       JOIN users u ON u.id = (CASE WHEN f.user_id=$1 THEN f.friend_id ELSE f.user_id END)
       WHERE f.user_id=$1 OR f.friend_id=$1`,
      [userId]
    );
    const out = [];
    for (const f of friends.rows) {
      const m = await db.query(
        `SELECT content, created_at, sender_id FROM messages
         WHERE (sender_id=$1 AND receiver_id=$2) OR (sender_id=$2 AND receiver_id=$1)
         ORDER BY id DESC LIMIT 1`,
        [userId, f.id]
      );
      const last = m.rows[0] || null;
      out.push({
        peerId: f.id, username: f.username, nickname: f.nickname,
        lastMsg: last ? last.content : "",
        lastTime: last ? last.created_at : null,
        lastFromMe: last ? last.sender_id === userId : false
      });
    }
    out.sort((a, b) => (b.lastTime || 0) < (a.lastTime || 0) ? -1 : 1);
    res.json({ ok: true, conversations: out });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
