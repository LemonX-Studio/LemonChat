package com.lemon.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ============ LemonChat 媒体管线 ============
// 压缩规则（2026-11-22 定稿）：
//   PNG（含透明）：≤30MB 原样直过；30~50MB 试无损重编码，有救就过；>50MB 拒
//   JPEG：≤10MB 过；10~30MB Q95→10MB 内（不行降 Q90/Q85 再来）；>30MB 拒
//   其他格式：≤10MB 过；10~30MB 转 JPEG Q95；>30MB 拒
//   视频：≤200MB 过；>200MB 拒
// 上传：APP 直传光鸭（阿里 OSS STS），不经过 Workers
// 下载：后端发临时直链（6h 有效），过期自动重取，文件落本地缓存
class LemonMedia {

    companion object {
        private const val IMG_LIMIT = 10 * 1024 * 1024
        private const val IMG_RECOMPRESS_MAX = 30 * 1024 * 1024
        private const val PNG_PASS_MAX = 30 * 1024 * 1024
        private const val PNG_HARD_MAX = 50 * 1024 * 1024
        private const val VIDEO_MAX = 200 * 1024 * 1024
        private const val MULTIPART_THRESHOLD = 1024 * 1024   // <1MB 走单次 PUT
        private const val PART_SIZE = 5L * 1024 * 1024

        private val http = OkHttpClient.Builder().build()
        private val jsonMt = "application/json; charset=utf-8".toMediaType()

        // 临时直链缓存：fileId → (url, 过期时间戳秒)
        private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()
        // 图片解码结果内存缓存：content → ImageBitmap 前身 Bitmap
        private val bmpCache = ConcurrentHashMap<String, Bitmap>()

        class UploadException(msg: String) : Exception(msg)

        data class Prepared(val bytes: ByteArray, val name: String, val mime: String)

        // ---------- 消息引用 ----------
        data class Ref(val fileId: String, val isVideo: Boolean, val name: String, val mime: String)

        fun refOf(content: String): Ref? {
            if (!content.startsWith("lemon://media/")) return null
            val body = content.removePrefix("lemon://media/")
            val fileId = body.substringBefore("?")
            val q = body.substringAfter("?", "")
            val params = q.split("&").mapNotNull {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) kv[0] to java.net.URLDecoder.decode(kv[1], "UTF-8") else null
            }.toMap()
            return Ref(fileId, params["t"] == "video", params["name"] ?: "", params["mime"] ?: "")
        }

        fun isImage(content: String): Boolean {
            if (content.startsWith("data:image/")) return true
            val r = refOf(content) ?: return false
            return !r.isVideo
        }

        fun isVideo(content: String): Boolean {
            if (content.startsWith("data:video/")) return true
            val r = refOf(content) ?: return false
            return r.isVideo
        }

        fun isMedia(content: String): Boolean = content.startsWith("data:image/") || content.startsWith("data:video/") || content.startsWith("lemon://media/")

        fun refContent(fileId: String, isVideo: Boolean, name: String, mime: String): String {
            val q = "t=" + (if (isVideo) "video" else "image") +
                "&name=" + java.net.URLEncoder.encode(name, "UTF-8") +
                "&mime=" + java.net.URLEncoder.encode(mime, "UTF-8")
            return "lemon://media/$fileId?$q"
        }

        fun displayNameOf(ctx: Context, uri: Uri): String = runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: "file_${System.currentTimeMillis()}"

        // ---------- 压缩规则 ----------
        suspend fun prepareImage(ctx: Context, uri: Uri, nameHint: String?): Prepared? = withContext(Dispatchers.IO) {
            val name = nameHint ?: "img_${System.currentTimeMillis()}"
            val mime = ctx.contentResolver.getType(uri) ?: guessMime(name)
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            when {
                mime.contains("png") -> {
                    // PNG：永不有损。≤30MB 直过；30~50 试无损重编码；>50 拒
                    when {
                        bytes.size <= PNG_PASS_MAX -> Prepared(bytes, name, mime)
                        bytes.size <= PNG_HARD_MAX -> {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                            val out = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                            val packed = out.toByteArray()
                            Prepared(if (packed.size < bytes.size) packed else bytes, name, mime)
                        }
                        else -> throw UploadException("PNG 超过 50MB，发不了哦")
                    }
                }
                mime.contains("jpeg") || mime.contains("jpg") -> when {
                    bytes.size <= IMG_LIMIT -> Prepared(bytes, name, mime)
                    bytes.size <= IMG_RECOMPRESS_MAX -> {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                        var q = 95
                        var out: ByteArray
                        do {
                            val bos = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, q, bos)
                            out = bos.toByteArray()
                            q -= 5
                        } while (out.size > IMG_LIMIT && q >= 85)
                        if (out.size > IMG_LIMIT) throw UploadException("图片压不进 10MB，请裁剪后再发")
                        Prepared(out, name, "image/jpeg")
                    }
                    else -> throw UploadException("图片超过 30MB，发不了哦")
                }
                else -> when {
                    bytes.size <= IMG_LIMIT -> Prepared(bytes, name, mime)
                    bytes.size <= IMG_RECOMPRESS_MAX -> {
                        // 其他格式 → 转 JPEG Q95
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                        val bos = java.io.ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, bos)
                        Prepared(bos.toByteArray(), name.removeSuffix(".${name.substringAfterLast('.', "")}") + ".jpg", "image/jpeg")
                    }
                    else -> throw UploadException("图片超过 30MB，发不了哦")
                }
            }
        }

        suspend fun prepareVideo(ctx: Context, uri: Uri, nameHint: String?): Prepared? = withContext(Dispatchers.IO) {
            val name = nameHint ?: "video_${System.currentTimeMillis()}.mp4"
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            if (bytes.size > VIDEO_MAX) throw UploadException("视频超过 200MB，发不了哦")
            Prepared(bytes, name, "video/mp4")
        }

        private fun guessMime(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return when (ext) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                else -> "application/octet-stream"
            }
        }

        // ---------- 上传（APP 直传光鸭 OSS）----------
        private fun md5B64(b: ByteArray) = b64(MessageDigest.getInstance("MD5").digest(b))
        private fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)

        private fun gmtDate(): String {
            val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("GMT")
            return fmt.format(Date())
        }

        // OSS V1 签名（与 guangyaclient oss_sign_headers 一致）
        private fun ossSign(method: String, bucket: String, objectKey: String, ak: String, sk: String, sts: String,
                            contentType: String, contentMd5: String, sub: Map<String, String>): Map<String, String> {
            val date = gmtDate()
            val resource = buildString {
                append("/").append(bucket).append("/").append(objectKey)
                if (sub.isNotEmpty()) {
                    append("?")
                    append(sub.entries.sortedBy { it.key }.joinToString("&") { (k, v) -> if (v.isEmpty()) k else "$k=$v" })
                }
            }
            val canonical = listOf(method.uppercase(), contentMd5, contentType, date,
                "x-oss-date:$date", "x-oss-security-token:${sts.trim()}", resource).joinToString("\n")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(sk.toByteArray(), "HmacSHA1"))
            val sig = b64(mac.doFinal(canonical.toByteArray()))
            return hashMapOf(
                "Authorization" to "OSS $ak:$sig",
                "x-oss-date" to date,
                "x-oss-security-token" to sts.trim(),
                "Date" to date,
                "Content-Type" to contentType
            ).also { if (contentMd5.isNotEmpty()) it["Content-MD5"] = contentMd5 }
        }

        private suspend fun upload(p: Prepared, isVideo: Boolean): String {
            val token = Api.mediaToken(p.name, p.bytes.size, if (p.bytes.size < MULTIPART_THRESHOLD) md5B64(p.bytes) else null, isVideo)
                .getOrElse { throw UploadException("服务器取凭证失败: ${it.message}") }
            val base = token.getString("fullEndPoint") + "/" + token.getString("objectPath")
            val creds = token.getJSONObject("creds")
            val ak = creds.getString("accessKeyID")
            val sk = creds.getString("secretAccessKey")
            val sts = creds.getString("sessionToken")
            val bucket = token.getString("bucketName")
            val objectKey = token.getString("objectPath")
            val taskId = token.getString("taskId")
            val ct = p.mime.ifEmpty { "application/octet-stream" }

            if (p.bytes.size < MULTIPART_THRESHOLD) {
                val headers = ossSign("PUT", bucket, objectKey, ak, sk, sts, ct, md5B64(p.bytes), emptyMap())
                val req = Request.Builder().url(base).put(p.bytes.toRequestBody(ct.toMediaType())).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
                http.newCall(req).execute().use { if (!it.isSuccessful) throw UploadException("直传失败 HTTP ${it.code}") }
            } else {
                // 分片上传
                val initHeaders = ossSign("POST", bucket, objectKey, ak, sk, sts, ct, "", mapOf("uploads" to ""))
                val initReq = Request.Builder().url("$base?uploads").post(byteArrayOf().toRequestBody(ct.toMediaType()))
                    .apply { initHeaders.forEach { (k, v) -> header(k, v) } }.build()
                val uploadId = http.newCall(initReq).execute().use { resp ->
                    val x = resp.body?.string() ?: ""
                    Regex("<UploadId>([^<]+)</UploadId>").find(x)?.groupValues?.get(1)
                        ?: throw UploadException("初始化分片失败 HTTP ${resp.code}")
                }
                val parts = mutableListOf<Pair<Int, String>>() // (partNumber, etag)
                var pn = 1
                var offset = 0L
                while (offset < p.bytes.size) {
                    val end = minOf(offset + PART_SIZE, p.bytes.size.toLong()).toInt()
                    val chunk = p.bytes.copyOfRange(offset.toInt(), end)
                    val sub = mapOf("partNumber" to pn.toString(), "uploadId" to uploadId)
                    val h = ossSign("PUT", bucket, objectKey, ak, sk, sts, ct, md5B64(chunk), sub)
                    val url = "$base?partNumber=$pn&uploadId=$uploadId"
                    val req = Request.Builder().url(url).put(chunk.toRequestBody(ct.toMediaType())).apply { h.forEach { (k, v) -> header(k, v) } }.build()
                    val etag = http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw UploadException("分片 $pn 上传失败 HTTP ${resp.code}")
                        resp.header("etag")?.trim('"') ?: throw UploadException("分片 $pn 缺 ETag")
                    }
                    parts.add(pn to etag)
                    pn++
                    offset = end.toLong()
                }
                val xml = buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><CompleteMultipartUpload>")
                    parts.sortedBy { it.first }.forEach { (n, e) -> append("<Part><PartNumber>$n</PartNumber><ETag>\"$e\"</ETag></Part>") }
                    append("</CompleteMultipartUpload>")
                }.toByteArray()
                val compHeaders = ossSign("POST", bucket, objectKey, ak, sk, sts, "application/xml", md5B64(xml), mapOf("uploadId" to uploadId))
                val compReq = Request.Builder().url("$base?uploadId=$uploadId").post(xml.toRequestBody("application/xml".toMediaType()))
                    .apply { compHeaders.forEach { (k, v) -> header(k, v) } }.build()
                http.newCall(compReq).execute().use { if (!it.isSuccessful) throw UploadException("合并分片失败 HTTP ${it.code}") }
            }
            // 轮询确认拿 fileId
            var last: JSONObject? = null
            for (i in 1..20) {
                val r = Api.mediaComplete(taskId).getOrNull()
                last = r
                val fid = r?.optString("fileId") ?: ""
                if (fid.isNotEmpty()) return fid
                delay(1500)
            }
            throw UploadException("上传确认超时")
        }

        // 发送辅助：压缩规则 → 直传 → 组引用（UI 层一行调用，自带协程与错误回调）
        fun sendMedia(ctx: Context, uri: Uri, displayName: String?, isVideo: Boolean,
                      onRef: (String) -> Unit, onFail: (String) -> Unit) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val prepared = if (isVideo) prepareVideo(ctx, uri, displayName) else prepareImage(ctx, uri, displayName)
                    prepared ?: throw UploadException("读取文件失败")
                    val fileId = upload(prepared, isVideo)
                    refContent(fileId, isVideo, prepared.name, prepared.mime)
                }.fold(
                    onSuccess = { withContext(Dispatchers.Main) { onRef(it) } },
                    onFailure = { withContext(Dispatchers.Main) { onFail(it.message ?: "上传失败") } }
                )
            }
        }

        // ---------- 下载 ----------
        private fun cacheFile(ctx: Context, fileId: String) = File(ctx.cacheDir, "lemonmedia/$fileId")

        private suspend fun freshUrl(fileId: String, force: Boolean = false): String {
            val now = System.currentTimeMillis() / 1000
            if (!force) urlCache[fileId]?.let { (u, exp) -> if (exp - 600 > now) return u }
            val r = Api.mediaUrl(fileId).getOrElse { throw Exception("取下载链接失败: ${it.message}") }
            val url = r.getString("url")
            val duration = r.optLong("duration", 21600)
            urlCache[fileId] = url to (now + duration)
            return url
        }

        private fun download(url: String): ByteArray {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("下载失败 HTTP ${resp.code}")
                return resp.body?.bytes() ?: throw Exception("下载为空")
            }
        }

        // 统一取媒体字节：data: 解 base64；lemon:// 走缓存+直链（过期自动重取一次）
        suspend fun loadBytes(ctx: Context, content: String): ByteArray? = withContext(Dispatchers.IO) {
            runCatching {
                if (content.startsWith("data:")) {
                    return@withContext android.util.Base64.decode(content.substringAfter("base64,"), android.util.Base64.DEFAULT)
                }
                val ref = refOf(content) ?: return@withContext null
                val f = cacheFile(ctx, ref.fileId)
                if (f.exists() && f.length() > 0) return@withContext f.readBytes()
                var bytes = try {
                    download(freshUrl(ref.fileId))
                } catch (e: Exception) {
                    // 链接过期/失效 → 强制刷新再试一次
                    download(freshUrl(ref.fileId, force = true))
                }
                runCatching { f.parentFile?.mkdirs(); f.writeBytes(bytes) }
                bytes
            }.getOrNull()
        }

        // 保存到相册 LemonChat 目录（图片→Pictures，视频→Movies）
        suspend fun saveToGallery(ctx: Context, content: String): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = loadBytes(ctx, content) ?: return@withContext false
                val ref = refOf(content)
                val isVideo = ref?.isVideo ?: content.startsWith("data:video/")
                val mime = ref?.mime?.ifEmpty { if (isVideo) "video/mp4" else "image/jpeg" }
                    ?: content.substringBefore(";").removePrefix("data:")
                val ext = when {
                    isVideo -> "mp4"
                    mime.contains("png") -> "png"
                    mime.contains("gif") -> "gif"
                    mime.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(
                    if (isVideo) android.os.Environment.DIRECTORY_MOVIES else android.os.Environment.DIRECTORY_PICTURES,
                ), "LemonChat").apply { mkdirs() }
                val out = File(dir, "lemon_${System.currentTimeMillis()}.$ext")
                out.writeBytes(bytes)
                true
            }.getOrDefault(false)
        }
    }
}
