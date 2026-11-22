// 光鸭云盘 token 管理 + API 封装（Workers 端）
// 事实：refresh_token 不轮换，access_token 约每小时过期，刷新后缓存内存即可

const ACCOUNT_BASE = "https://account.guangyapan.com";
const API_BASE = "https://api.guangyapan.com";
const CLIENT_ID = "aMe-8VSlkrbQXpUR";

// lemonchat 媒体目录（2026-11-22 实测创建）
export const DIR_PICTURES = "1970851804827172887";
export const DIR_VIDEOS = "1970851805426954311";

// access_token 内存缓存（同 isolate 内复用）
let _tok = { token: "", exp: 0 };

function baseHeaders(did) {
  return {
    accept: "*/*",
    "content-type": "application/json",
    origin: "https://www.guangyapan.com",
    referer: "https://www.guangyapan.com/",
    "user-agent":
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
    did,
  };
}

export function guangyaDid(env) {
  return env.GUANGYA_DID || "lemonchat-worker-0001";
}

// 刷新 access_token（带 5 分钟提前量）
export async function getAccessToken(env, force = false) {
  const now = Date.now() / 1000;
  if (!force && _tok.token && now < _tok.exp - 300) return _tok.token;
  const r = await fetch(`${ACCOUNT_BASE}/v1/auth/token`, {
    method: "POST",
    headers: { ...baseHeaders(guangyaDid(env)), "x-action": "401" },
    body: JSON.stringify({
      client_id: CLIENT_ID,
      grant_type: "refresh_token",
      refresh_token: env.GUANGYA_REFRESH_TOKEN,
    }),
  });
  const d = await r.json().catch(() => ({}));
  if (!d.access_token) throw new Error("光鸭刷新失败: " + JSON.stringify(d).slice(0, 200));
  _tok = { token: d.access_token, exp: d.expires_in ? now + d.expires_in : now + 3500 };
  return _tok.token;
}

// 带鉴权的 API 请求，401 自动刷新重试一次
export async function guangyaApi(env, path, body) {
  const did = guangyaDid(env);
  const call = async (token) =>
    fetch(`${API_BASE}${path}`, {
      method: "POST",
      headers: { ...baseHeaders(did), authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    });
  let token = await getAccessToken(env);
  let r = await call(token);
  if (r.status === 401) {
    token = await getAccessToken(env, true);
    r = await call(token);
  }
  return r.json().catch(() => ({}));
}

// 取上传凭证（APP 直传 OSS 用）
export async function mediaUploadToken(env, name, fileSize, md5, isVideo) {
  const parentId = isVideo ? DIR_VIDEOS : DIR_PICTURES;
  const res = { fileSize };
  if (md5) res.md5 = md5;
  const d = await guangyaApi(env, "/nd.bizuserres.s/v1/get_res_center_token", {
    capacity: 2,
    name,
    res,
    parentId,
  });
  if (d.msg !== "success" || !d.data?.taskId) throw new Error("取上传凭证失败: " + JSON.stringify(d).slice(0, 200));
  return d.data;
}

// 轮询上传任务直到完成，返回 fileId
export async function mediaComplete(env, taskId) {
  for (let i = 0; i < 20; i++) {
    const d = await guangyaApi(env, "/nd.bizuserres.s/v1/file/get_info_by_task_id", { taskId });
    if (d.msg === "success" && d.data?.fileId) return d.data;
    if (d.code !== 147) throw new Error("上传确认失败: " + JSON.stringify(d).slice(0, 200));
    await new Promise((r) => setTimeout(r, 1500));
  }
  throw new Error("上传确认超时");
}

// 取匿名下载直链（6h 有效）
export async function mediaUrl(env, fileId) {
  const d = await guangyaApi(env, "/nd.bizuserres.s/v1/get_res_download_url", { fileId });
  if (d.msg !== "success" || !d.data?.signedURL) throw new Error("取下载链接失败: " + JSON.stringify(d).slice(0, 200));
  return { url: d.data.signedURL, duration: d.data.urlDuration || 21600 };
}
