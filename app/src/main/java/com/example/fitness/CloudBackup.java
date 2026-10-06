package com.example.fitness;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 七牛云 Kodo 云备份：纯 JDK/Android 实现的签名与上传/下载，无第三方依赖。
 * - 上传：表单上传（multipart），token = AK:HMAC-SHA1(urlsafe_b64(putPolicy)):urlsafe_b64(putPolicy)
 *   scope 必须逐 key 签（"bucket:key"），空间级 scope 覆盖已存在 key 会报 614
 * - 下载：私有空间签名 URL（url + "?e=" + deadline，对含 e 的完整 URL 签 HMAC-SHA1 后追加 "&token=AK:sign"）
 * - cfg 结构：{ak, sk, bucket, region, dailyKey, latestKey, dlDomain}
 */
public final class CloudBackup {

    /** 完成回调：resultJson 形如 {"ok":true,...} 或 {"ok":false,"error":"..."} */
    public interface Cb { void done(String resultJson); }

    private static final int TIMEOUT_MS = 15000;
    private static final String BOUNDARY = "----FitnessFormBoundary7MA4YWxkTrZu0gW";

    private CloudBackup() {}

    /** 上传备份：dailyKey（按日留档）+ latestKey（恢复用）串行上传，任一失败即报错 */
    public static void upload(String cfgJson, String payload, Cb cb) {
        String result;
        try {
            JSONObject cfg = new JSONObject(cfgJson == null ? "{}" : cfgJson);
            String ak = cfg.optString("ak", "").trim();
            String sk = cfg.optString("sk", "").trim();
            String bucket = cfg.optString("bucket", "").trim();
            String region = cfg.optString("region", "z0").trim();
            String dailyKey = cfg.optString("dailyKey", "");
            String latestKey = cfg.optString("latestKey", "");
            if (ak.isEmpty() || sk.isEmpty() || bucket.isEmpty()) {
                throw new IllegalArgumentException("缺少 AK / SK / 空间名配置");
            }
            if (dailyKey.isEmpty() || latestKey.isEmpty()) {
                throw new IllegalArgumentException("缺少上传 key");
            }
            String host = upHost(region);
            byte[] data = (payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8);
            long nowSec = System.currentTimeMillis() / 1000L;
            multipartUpload(host, dailyKey, uploadToken(ak, sk, bucket, dailyKey, nowSec), data, dailyKey);
            multipartUpload(host, latestKey, uploadToken(ak, sk, bucket, latestKey, nowSec), data, latestKey);
            result = "{\"ok\":true,\"ts\":" + System.currentTimeMillis() + "}";
        } catch (Throwable t) {
            result = "{\"ok\":false,\"error\":" + JSONObject.quote(safeMsg(t)) + "}";
        }
        cb.done(result);
    }

    /** 下载备份文本：sk/ak 齐全时按私有空间签名，否则按公开空间直接拼域名 */
    public static void download(String cfgJson, String key, Cb cb) {
        String result;
        try {
            JSONObject cfg = new JSONObject(cfgJson == null ? "{}" : cfgJson);
            String domain = cfg.optString("dlDomain", "").trim();
            if (domain.isEmpty()) {
                throw new IllegalArgumentException("缺少下载域名（见七牛空间「域名管理」的测试/自定义域名）");
            }
            String url = normalizeDomain(domain) + Uri.encode(key, "/");
            String ak = cfg.optString("ak", "").trim();
            String sk = cfg.optString("sk", "").trim();
            if (!ak.isEmpty() && !sk.isEmpty()) {
                long e = System.currentTimeMillis() / 1000L + 600;
                String signed = url + "?e=" + e;
                signed += "&token=" + ak + ":" + sign(sk, signed);
                url = signed;
            }
            String body = httpGet(url);
            result = "{\"ok\":true,\"content\":" + JSONObject.quote(body) + "}";
        } catch (Throwable t) {
            result = "{\"ok\":false,\"error\":" + JSONObject.quote(safeMsg(t)) + "}";
        }
        cb.done(result);
    }

    /* ==================== 七牛签名 ==================== */

    /** uploadToken = AK:urlsafe_b64(HMAC-SHA1(SK, encodedPolicy)):encodedPolicy，putPolicy scope 固定 bucket:key */
    private static String uploadToken(String ak, String sk, String bucket, String key, long nowSec) throws Exception {
        String policy = "{\"scope\":\"" + bucket + ":" + key + "\",\"deadline\":" + (nowSec + 3600) + "}";
        String encoded = Base64.encodeToString(policy.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP);
        return ak + ":" + sign(sk, encoded) + ":" + encoded;
    }

    /** HMAC-SHA1 → urlsafe base64（保留 = 填充，与七牛官方 SDK 一致） */
    private static String sign(String sk, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(sk.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(raw, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    /* ==================== HTTP ==================== */

    /** 表单上传：multipart 字段 key / token / file（必须带 filename） */
    private static void multipartUpload(String host, String key, String token, byte[] data, String fileName) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://" + host + "/").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writeField(bos, "key", key);
        writeField(bos, "token", token);
        bos.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        bos.write(data);
        bos.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] body = bos.toByteArray();
        conn.setFixedLengthStreamingMode(body.length);
        OutputStream os = null;
        try {
            os = conn.getOutputStream();
            os.write(body);
        } finally {
            if (os != null) os.close();
        }
        int code = conn.getResponseCode();
        String resp = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code + (resp.isEmpty() ? "" : "：" + extractError(resp)));
        }
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        int code = conn.getResponseCode();
        String resp = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code + (resp.isEmpty() ? "" : "：" + extractError(resp)));
        }
        return resp;
    }

    /* ==================== 工具 ==================== */

    /** 区域 → 上传域名：z0/z1/z2/na0/as0 及新区域名直接映射 up-<region>.qiniup.com */
    private static String upHost(String region) {
        String r = (region == null || region.trim().isEmpty()) ? "z0" : region.trim();
        return "up-" + r + ".qiniup.com";
    }

    private static String normalizeDomain(String domain) {
        String d = domain.trim();
        while (d.endsWith("/")) d = d.substring(0, d.length() - 1);
        if (!d.startsWith("http://") && !d.startsWith("https://")) d = "https://" + d;
        return d + "/";
    }

    private static void writeField(ByteArrayOutputStream bos, String name, String value) throws Exception {
        bos.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(value.getBytes(StandardCharsets.UTF_8));
        bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /** 从七牛错误响应（{"error":"..."}）里提取可读文本 */
    private static String extractError(String resp) {
        try { return new JSONObject(resp).optString("error", resp); } catch (Throwable t) { return resp; }
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        } finally {
            is.close();
        }
        return bos.toString("UTF-8");
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isEmpty()) ? t.toString() : m;
    }
}
