import { getPool, initDb } from "../lib/db.js";
import { json, queryParams, readBody } from "../lib/http.js";

// 群/团队体系
// 普通 group：gtype='normal'，无 parent
// 团队根：gtype='team'（总部大厅），下面可挂 部门群'dept' / 小组群'sub' / 临时群'temp'
// 限制：总部直属子群 ≤15；更深层每群 ≤8；最多嵌套 3 层；临时群不可再嵌套、最长 180 天
const ROOT_MAX = 15, CHILD_MAX = 8, MAX_DEPTH = 3;

// 清理过期临时群
async function cleanupExpired(db) {
  const ex = await db.query("SELECT id FROM groups WHERE gtype='temp' AND expires_at IS NOT NULL AND expires_at < now()");
  for (const g of ex.rows) {
    await db.query("DELETE FROM group_requests WHERE group_id=$1", [g.id]);
    await db.query("DELETE FROM group_messages WHERE group_id=$1", [g.id]);
    await db.query("DELETE FROM group_members WHERE group_id=$1", [g.id]);
    await db.query("DELETE FROM groups WHERE id=$1", [g.id]);
  }
}

// 向上找祖先链（含自己），深度最多 3
async function chainOf(db, groupId) {
  const chain = [];
  let cur = Number(groupId);
  for (let i = 0; i < MAX_DEPTH + 1; i++) {
    const r = await db.query("SELECT id, name, gtype, parent_id, owner_id, expires_at, open_join FROM groups WHERE id=$1", [cur]);
    if (!r.rows.length) return chain;
    chain.unshift(r.rows[0]);
    if (r.rows[0].parent_id == null) break;
    cur = r.rows[0].parent_id;
  }
  return chain;
}

async function childCount(db, groupId) {
  const r = await db.query("SELECT count(*)::int AS c FROM groups WHERE parent_id=$1", [Number(groupId)]);
  return r.rows[0].c;
}

export default async function onRequest(context) {
  const { request } = context;
  try {
    await initDb();
    const db = getPool();
    const url = new URL(request.url);

    if (request.method === "POST") {
      const { userId, name, groupId, action, username, gtype, ttlDays, parentId, open, requestId } = await readBody(request);
      if (!userId) return json({ ok: false, error: "缺userId" }, 400);

      // 建子群（团队套娃）
      if (action === "createChild") {
        if (parentId == null || parentId === "" || !name || !gtype) return json({ ok: false, error: "参数不齐" }, 400);
        if (!["dept", "sub", "temp"].includes(gtype)) return json({ ok: false, error: "类型不合法" }, 400);
        const chain = await chainOf(db, parentId);
        if (!chain.length) return json({ ok: false, error: "父群不存在" }, 404);
        const parent = chain[chain.length - 1];
        if (parent.gtype === "temp") return json({ ok: false, error: "临时群里不能再建群" }, 400);
        if (chain.length >= MAX_DEPTH) return json({ ok: false, error: "最多嵌套 3 层" }, 400);
        // 权限：父群成员 或 团长
        const root = chain[0];
        const isTeamOwner = root.gtype === "team" && root.owner_id === Number(userId);
        const mem = await db.query("SELECT 1 FROM group_members WHERE group_id=$1 AND user_id=$2", [parent.id, Number(userId)]);
        if (!mem.rows.length && !isTeamOwner) return json({ ok: false, error: "你不是该群成员" }, 403);
        // 数量限制
        const cc = await childCount(db, parent.id);
        const cap = parent.parent_id === null ? ROOT_MAX : CHILD_MAX;
        if (cc >= cap) return json({ ok: false, error: `该群下子群已满（${cap} 个）` }, 400);
        // 临时群有效期
        let expires = null;
        if (gtype === "temp") {
          const d = Number(ttlDays);
          if (d > 0) {
            const days = Math.min(180, Math.max(1, d));
            expires = new Date(Date.now() + days * 86400000);
          }
        }
        // 小群编号：父群号-类型字母+序号（t=临时 d=部门 g=小组），如 1-t1 / 1-d2
        const letter = { temp: "t", dept: "d", sub: "g" }[gtype];
        const seqR = await db.query(
          "SELECT coalesce(max(substring(code from '[0-9]+$')::int), 0) AS m FROM groups WHERE parent_id=$1 AND gtype=$2",
          [parent.id, gtype]
        );
        const code = `${parent.id}-${letter}${seqR.rows[0].m + 1}`;
        // 小群专用号段：从 1e15 起领号，不占用普通群的低号段
        const r = await db.query(
          "INSERT INTO groups (id, name, owner_id, parent_id, gtype, expires_at, code) VALUES (nextval('child_group_seq'),$1,$2,$3,$4,$5,$6) RETURNING id, name, gtype, code",
          [String(name).trim().slice(0, 30), Number(userId), parent.id, gtype, expires, code]
        );
        await db.query(
          "INSERT INTO group_members (group_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING",
          [r.rows[0].id, Number(userId)]
        );
        return json({ ok: true, group: r.rows[0] });
      }

      // 团长直接加入子群（不经过邀请）
      if (action === "ownerJoin") {
        if (groupId == null || groupId === "") return json({ ok: false, error: "缺groupId" }, 400);
        const chain = await chainOf(db, groupId);
        const root = chain[0];
        if (!root || root.gtype !== "team" || root.owner_id !== Number(userId))
          return json({ ok: false, error: "只有团长能用" }, 403);
        await db.query(
          "INSERT INTO group_members (group_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING",
          [Number(groupId), Number(userId)]
        );
        return json({ ok: true });
      }

      // 开放/关闭自由加入（群主或团长）
      if (action === "setOpen") {
        if (groupId == null || groupId === "") return json({ ok: false, error: "缺groupId" }, 400);
        const chain = await chainOf(db, groupId);
        const root = chain[0];
        const target = chain[chain.length - 1];
        const isTeamOwner = root.gtype === "team" && root.owner_id === Number(userId);
        if (target.owner_id !== Number(userId) && !isTeamOwner)
          return json({ ok: false, error: "无权限" }, 403);
        await db.query("UPDATE groups SET open_join=$1 WHERE id=$2", [!!open, Number(groupId)]);
        return json({ ok: true, open: !!open });
      }

      // 加群：开放群直接进；需申请的群 → 生成申请等群主通过
      if (action === "joinDirect") {
        if (groupId == null || groupId === "") return json({ ok: false, error: "缺groupId" }, 400);
        const chain = await chainOf(db, groupId);
        const root = chain[0];
        const target = chain[chain.length - 1];
        const isTeamOwner = root.gtype === "team" && root.owner_id === Number(userId);
        const im = await db.query("SELECT 1 FROM group_members WHERE group_id=$1 AND user_id=$2", [Number(groupId), Number(userId)]);
        if (im.rows.length) return json({ ok: true });
        if (!target.open_join && !isTeamOwner) {
          const dup = await db.query(
            "SELECT 1 FROM group_requests WHERE group_id=$1 AND user_id=$2 AND status='pending'",
            [Number(groupId), Number(userId)]);
          if (!dup.rows.length)
            await db.query("INSERT INTO group_requests (group_id, user_id) VALUES ($1,$2)", [Number(groupId), Number(userId)]);
          return json({ ok: true, pending: true, msg: "已发送申请，等群主通过" });
        }
        await db.query(
          "INSERT INTO group_members (group_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING",
          [Number(groupId), Number(userId)]
        );
        return json({ ok: true });
      }

      // 邀请成员（群成员 或 团长）：需申请的群 → 生成申请（带邀请人），群主通过后才进群
      if (action === "invite") {
        if (groupId == null || groupId === "" || !username) return json({ ok: false, error: "参数不齐" }, 400);
        const chain = await chainOf(db, groupId);
        const root = chain[0];
        const isTeamOwner = root.gtype === "team" && root.owner_id === Number(userId);
        const im = await db.query("SELECT 1 FROM group_members WHERE group_id=$1 AND user_id=$2", [Number(groupId), Number(userId)]);
        if (!im.rows.length && !isTeamOwner) return json({ ok: false, error: "你不在群里" }, 403);
        const t = await db.query("SELECT id FROM users WHERE username=$1", [String(username).trim()]);
        if (!t.rows.length) return json({ ok: false, error: "用户不存在" }, 404);
        const target = chain[chain.length - 1];
        const already = await db.query("SELECT 1 FROM group_members WHERE group_id=$1 AND user_id=$2", [Number(groupId), t.rows[0].id]);
        if (already.rows.length) return json({ ok: false, error: "TA已经在群里了" }, 400);
        if (!target.open_join && !isTeamOwner) {
          const dup = await db.query(
            "SELECT 1 FROM group_requests WHERE group_id=$1 AND user_id=$2 AND status='pending'",
            [Number(groupId), t.rows[0].id]);
          if (dup.rows.length) return json({ ok: true, pending: true, msg: "TA的进群申请已在队列里" });
          await db.query("INSERT INTO group_requests (group_id, user_id, inviter_id) VALUES ($1,$2,$3)",
            [Number(groupId), t.rows[0].id, Number(userId)]);
          return json({ ok: true, pending: true, msg: "已提交，等群主通过" });
        }
        await db.query(
          "INSERT INTO group_members (group_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING",
          [Number(groupId), t.rows[0].id]
        );
        return json({ ok: true });
      }

      // 群申请列表（群主/团长）
      if (action === "requests") {
        if (groupId == null || groupId === "") return json({ ok: false, error: "缺groupId" }, 400);
        const chain = await chainOf(db, groupId);
        const root = chain[0];
        const target = chain[chain.length - 1];
        const isTeamOwner = root.gtype === "team" && root.owner_id === Number(userId);
        if (target.owner_id !== Number(userId) && !isTeamOwner)
          return json({ ok: false, error: "只有群主或团长能看申请" }, 403);
        const r = await db.query(
          `SELECT q.id, q.user_id, q.inviter_id, u.nickname AS applicant, u.username AS app_username,
                  COALESCE(v.nickname, '') AS inviter
           FROM group_requests q
           JOIN users u ON u.id = q.user_id
           LEFT JOIN users v ON v.id = q.inviter_id
           WHERE q.group_id=$1 AND q.status='pending' ORDER BY q.id ASC`,
          [Number(groupId)]);
        return json({ ok: true, requests: r.rows, open: !!target.open_join });
      }

      // 处理申请：通过 / 拒绝（群主/团长）
      if (action === "approve" || action === "reject") {
        if (!requestId) return json({ ok: false, error: "缺requestId" }, 400);
        const q = await db.query("SELECT q.*, g.owner_id FROM group_requests q JOIN groups g ON g.id=q.group_id WHERE q.id=$1", [Number(requestId)]);
        if (!q.rows.length) return json({ ok: false, error: "申请不存在" }, 404);
        const req = q.rows[0];
        const chain = await chainOf(db, req.group_id);
        const root = chain[0];
        const target = chain[chain.length - 1];
        const isTeamOwner = root.gtype === "team" && root.owner_id === Number(userId);
        if (target.owner_id !== Number(userId) && !isTeamOwner)
          return json({ ok: false, error: "只有群主或团长能处理申请" }, 403);
        await db.query("UPDATE group_requests SET status=$1 WHERE id=$2", [action === "approve" ? "approved" : "rejected", Number(requestId)]);
        if (action === "approve")
          await db.query("INSERT INTO group_members (group_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING",
            [req.group_id, req.user_id]);
        return json({ ok: true });
      }

      // 退群
      if (action === "leave") {
        if (groupId == null || groupId === "") return json({ ok: false, error: "缺groupId" }, 400);
        const g = await db.query("SELECT owner_id, parent_id FROM groups WHERE id=$1", [Number(groupId)]);
        if (!g.rows.length) return json({ ok: false, error: "群不存在" }, 404);
        if (g.rows[0].owner_id === Number(userId)) return json({ ok: false, error: "群主不能退出，请解散群聊" }, 400);
        await db.query("DELETE FROM group_members WHERE group_id=$1 AND user_id=$2", [Number(groupId), Number(userId)]);
        return json({ ok: true });
      }

      // 解散：该群群主 或 团长；连子群一起
      if (action === "dissolve") {
        if (groupId == null || groupId === "") return json({ ok: false, error: "缺groupId" }, 400);
        const chain = await chainOf(db, groupId);
        const root = chain[0];
        const target = chain[chain.length - 1];
        const isTeamOwner = root.gtype === "team" && root.owner_id === Number(userId);
        if (target.owner_id !== Number(userId) && !isTeamOwner)
          return json({ ok: false, error: "只有群主或团长能解散" }, 403);
        const ids = chain.length ? [target.id] : [];
        // 收集所有后代
        let frontier = [target.id];
        for (let i = 0; i < MAX_DEPTH; i++) {
          const r = await db.query("SELECT id FROM groups WHERE parent_id = ANY($1)", [frontier]);
          if (!r.rows.length) break;
          frontier = r.rows.map((x) => x.id);
          ids.push(...frontier);
        }
        await db.query("DELETE FROM group_messages WHERE group_id = ANY($1)", [ids]);
        await db.query("DELETE FROM group_requests WHERE group_id = ANY($1)", [ids]);
        await db.query("DELETE FROM group_members WHERE group_id = ANY($1)", [ids]);
        await db.query("DELETE FROM groups WHERE id = ANY($1)", [ids]);
        return json({ ok: true });
      }

      // 建群/建团队根（没有 action 也没有 name 的请求一律拒绝，防止加群误建群）
      if (!action && !name) return json({ ok: false, error: "缺参数" }, 400);
      const r = await db.query(
        "INSERT INTO groups (name, owner_id, gtype) VALUES ($1,$2,$3) RETURNING id, name, gtype",
        [String(name || "").trim().slice(0, 30), Number(userId), gtype === "team" ? "team" : "normal"]
      );
      await db.query(
        "INSERT INTO group_members (group_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING",
        [r.rows[0].id, Number(userId)]
      );
      return json({ ok: true, group: r.rows[0] });
    }

    if (request.method === "GET") {
      // 子树查询（团队页/列表图标）
      if (url.pathname.endsWith("/tree")) {
        const rootId = Number(url.searchParams.get("rootId"));
        const userId = Number(url.searchParams.get("userId"));
        if (Number.isNaN(rootId) || Number.isNaN(userId)) return json({ ok: false, error: "缺参数" }, 400);
        await cleanupExpired(db);
        const rootR = await db.query("SELECT * FROM groups WHERE id=$1", [rootId]);
        if (!rootR.rows.length) return json({ ok: false, error: "团队不存在" }, 404);
        // 收集子树
        const all = [rootR.rows[0]];
        let frontier = [rootId];
        for (let i = 0; i < MAX_DEPTH; i++) {
          const r = await db.query(
            `SELECT g.id, g.name, g.gtype, g.parent_id, g.owner_id, g.expires_at, g.open_join, g.code,
                    (SELECT count(*)::int FROM group_members m2 WHERE m2.group_id = g.id) AS members
             FROM groups g WHERE g.parent_id = ANY($1) ORDER BY g.id`,
            [frontier]
          );
          if (!r.rows.length) break;
          frontier = r.rows.map((x) => x.id);
          all.push(...r.rows);
        }
        const mem = await db.query("SELECT group_id FROM group_members WHERE user_id=$1", [userId]);
        const mine = new Set(mem.rows.map((x) => x.group_id));
        const rootOwner = rootR.rows[0].owner_id;
        return json({
          ok: true,
          rootOwner,
          groups: all.map((g) => ({ ...g, isMember: mine.has(g.id) })),
        });
      }

      // 搜群：支持小群编号（如 1-t1 / 1-d2 / 1-g3）或纯群号（如 5）
      if (url.pathname.endsWith("/search")) {
        const q = String(url.searchParams.get("q") || "").trim().toLowerCase();
        if (!q) return json({ ok: false, error: "缺参数" }, 400);
        await cleanupExpired(db);
        let g = null;
        const m = q.match(/^(\d+)-([tdg])(\d+)$/);
        if (m) {
          const r = await db.query(
            `SELECT g.id, g.name, g.gtype, g.code, g.open_join,
                    (SELECT count(*)::int FROM group_members m2 WHERE m2.group_id = g.id) AS members
             FROM groups g WHERE g.parent_id=$1 AND g.code=$2`, [Number(m[1]), q]);
          g = r.rows[0] || null;
        } else if (/^\d+$/.test(q)) {
          const r = await db.query(
            `SELECT g.id, g.name, g.gtype, g.code, g.open_join,
                    (SELECT count(*)::int FROM group_members m2 WHERE m2.group_id = g.id) AS members
             FROM groups g WHERE g.id=$1 AND g.parent_id IS NULL`, [Number(q)]);
          g = r.rows[0] || null;
        }
        if (!g) return json({ ok: false, error: "没找到这个群" }, 404);
        // 往上找团队根，展示「团队群名 + 团队群号」
        let root = { id: g.id, name: g.name };
        if (g.id !== Number(q) || m) {
          const chain = await chainOf(db, g.id);
          if (chain.length) root = { id: chain[0].id, name: chain[0].name };
        }
        return json({ ok: true, root, group: g });
      }

      // 顶层列表：普通群 + 团队根
      const userId = Number(queryParams(request).userId);
      if (!userId) return json({ ok: false, error: "缺userId" }, 400);
      await cleanupExpired(db);
      const r = await db.query(
        `SELECT g.id, g.name, g.gtype, g.owner_id, u.nickname AS owner,
                (SELECT count(*)::int FROM group_members m2 WHERE m2.group_id = g.id) AS members
         FROM groups g
         JOIN group_members m ON m.group_id = g.id AND m.user_id = $1
         JOIN users u ON u.id = g.owner_id
         WHERE g.parent_id IS NULL
         ORDER BY g.id ASC`,
        [userId]
      );
      return json({ ok: true, groups: r.rows });
    }

    return json({ ok: false, error: "只支持GET/POST" }, 405);
  } catch (e) {
    return json({ ok: false, error: "服务器开小差了" }, 500);
  }
}
