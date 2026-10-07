const crypto = require("crypto");
const { getPool, initDb } = require("./_db");

function hash(pw, salt) {
  return crypto.createHash("sha256").update(salt + ":" + pw).digest("hex");
}

// GET /api/forgot?username=x  拿密保问题
// POST /api/forgot {username, secA, newPassword}  答对密保重置密码
export default async function handler(req, res) {
  try {
    await initDb();
    const db = getPool();
    if (req.method === "GET") {
      const u = await db.query("SELECT sec_q FROM users WHERE username=$1", [req.query.username || ""]);
      if (!u.rows.length || !u.rows[0].sec_q)
        return res.status(400).json({ ok: false, error: "该用户没设置密保问题，找管理员重置吧" });
      return res.json({ ok: true, secQ: u.rows[0].sec_q });
    }
    if (req.method === "POST") {
      const { username, secA, newPassword } = req.body || {};
      if (!username || !secA || !newPassword) return res.status(400).json({ ok: false, error: "填全啊" });
      if (newPassword.length < 6) return res.status(400).json({ ok: false, error: "新密码至少6位" });
      const r = await db.query("SELECT sec_a FROM users WHERE username=$1", [username]);
      if (!r.rows.length) return res.status(400).json({ ok: false, error: "用户不存在" });
      if ((r.rows[0].sec_a || "").trim().toLowerCase() !== secA.trim().toLowerCase())
        return res.status(400).json({ ok: false, error: "密保答案不对" });
      const salt = crypto.randomBytes(8).toString("hex");
      await db.query("UPDATE users SET pass_hash=$1 WHERE username=$2", [salt + ":" + hash(newPassword, salt), username]);
      return res.json({ ok: true });
    }
    res.status(405).json({ ok: false, error: "只支持GET/POST" });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
