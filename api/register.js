const crypto = require("crypto");
const { getPool, initDb } = require("./_db");

function hash(pw, salt) {
  return crypto.createHash("sha256").update(salt + ":" + pw).digest("hex");
}

export default async function handler(req, res) {
  if (req.method !== "POST") return res.status(405).json({ ok: false, error: "只支持POST" });
  const { username, password, nickname, secQ, secA } = req.body || {};
  if (!username || !password || !nickname)
    return res.status(400).json({ ok: false, error: "用户名/密码/昵称不能为空" });
  if (!/^[a-zA-Z0-9_]{3,20}$/.test(username))
    return res.status(400).json({ ok: false, error: "用户名需3-20位字母数字下划线" });
  if (password.length < 6)
    return res.status(400).json({ ok: false, error: "密码至少6位" });
  try {
    await initDb();
    const db = getPool();
    const exists = await db.query("SELECT id FROM users WHERE username=$1", [username]);
    if (exists.rows.length) return res.status(400).json({ ok: false, error: "用户名已被占用" });
    const salt = crypto.randomBytes(8).toString("hex");
    const r = await db.query(
      "INSERT INTO users (username, nickname, pass_hash, sec_q, sec_a) VALUES ($1,$2,$3,$4,$5) RETURNING id, nickname",
      [username, nickname, hash(password, salt), secQ || "", secA || ""]
    );
    res.json({ ok: true, user: { id: r.rows[0].id, nickname: r.rows[0].nickname } });
  } catch (e) {
    res.status(500).json({ ok: false, error: "服务器开小差了" });
  }
}
