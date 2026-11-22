import { json, readBody } from "../lib/http.js";
import { mediaUploadToken, mediaComplete, mediaUrl, guangyaDid } from "../lib/guangya.js";

// /api/media/token  POST {name, size, md5?, isVideo}  → 光鸭上传凭证（APP 直传 OSS）
// /api/media/complete POST {taskId} → {fileId,...}
// /api/media/url   POST {fileId} → {url, duration}
export default async function onRequest(context) {
  const { request, env } = context;
  try {
    if (!env.GUANGYA_REFRESH_TOKEN) return json({ ok: false, error: "光鸭未配置" }, 500);
    const { pathname } = new URL(request.url);
    const action = pathname.split("/")[3] || "";
    const body = await readBody(request);

    if (action === "token") {
      if (!body.name || !body.size) return json({ ok: false, error: "缺参数" }, 400);
      const data = await mediaUploadToken(env, body.name, Number(body.size), body.md5 || null, !!body.isVideo);
      return json({ ok: true, upload: data, did: guangyaDid(env) });
    }
    if (action === "complete") {
      if (!body.taskId) return json({ ok: false, error: "缺taskId" }, 400);
      const data = await mediaComplete(env, body.taskId);
      return json({ ok: true, fileId: data.fileId, fileName: data.fileName, fileSize: data.fileSize });
    }
    if (action === "url") {
      if (!body.fileId) return json({ ok: false, error: "缺fileId" }, 400);
      const { url, duration } = await mediaUrl(env, body.fileId);
      return json({ ok: true, url, duration });
    }
    return json({ ok: false, error: "未知操作" }, 404);
  } catch (e) {
    return json({ ok: false, error: String(e?.message || e).slice(0, 300) }, 500);
  }
}
