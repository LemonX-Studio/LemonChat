const { getPool, initDb } = require("./_db");

// GET /api/messages?userId=1&peerId=2&after=0  拉消息
// POST /api/messages {senderId, receiverId, content}  发消息
export default async function handler(req, res) {
  try {
    await initDb();
    const db = getPool();
    if (req.method === "GET") {
      const userId = Number(req.query.userId);
      const peerId = Number(req.query.peerId);
      const after = Number(req.query.after || 0);
      if (!userId || !peerId) return res.status(400).json({ ok: false, error: "缺参数" });
      const r = await db.query(
        `SELECT id, sender_id, content, created_at FROM messages
         WHERE ((sender_id=$1 AND receiver_id=$2) OR (sender_id=$2 AND receiver_id=$1)) AND id > $3
         ORDER BY id ASC LIMIT 200`,
        [userId, peerId, after]
      );
      return res.json({ ok: true, messages: r.rows });
    }
    if (req.method === "POST") {
      const { senderId, receiverId, content } = req.body || {};
      if (!senderId || !receiverId || !content)
        return res.status(400).json({ ok: false, error: "参数不齐" });
      if (typeof content !== "string" || content.length > 2000)
        return res.status(400).json({ ok: false, error: "消息过长" });
      const r = await db.query(
        "INSERT INTO messages (sender_id, receiver_id, content) VALUES ($1,$2,$3) RETURNING id, created_at",
        [senderId, receiverId, content]
      );
      await db.query("UPDATE users SET last_seen=now() WHERE id IN ($1,$2)", [senderId, receiverId]);
      return res.json({ ok: true, id: r.rows[0].id, created_at: r.rows[0].created_at });
    }
    res.status(405).json({ ok: false, error: "只支持GET/POST" });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
