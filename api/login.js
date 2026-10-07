const crypto = require("crypto");
const { getPool, initDb } = require("./_db");

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ ok: false, error: "只支持POST" });
  const { username, password } = req.body || {};
  if (!username || !password) return res.status(400).json({ ok: false, error: "填写用户名和密码" });
  try {
    await initDb();
    const db = getPool();
    const r = await db.query("SELECT id, nickname, pass_hash, is_official FROM users WHERE username=$1", [username]);
    if (!r.rows.length) return res.status(400).json({ ok: false, error: "用户不存在" });
    const [salt, ...rest] = r.rows[0].pass_hash.split(":");
    const h = crypto.createHash("sha256").update(salt + ":" + password).digest("hex");
    if (h !== rest.join(":")) return res.status(400).json({ ok: false, error: "密码不对" });
    await db.query("UPDATE users SET last_seen=now() WHERE id=$1", [r.rows[0].id]);
    res.json({ ok: true, user: { id: r.rows[0].id, nickname: r.rows[0].nickname, is_official: r.rows[0].is_official } });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
