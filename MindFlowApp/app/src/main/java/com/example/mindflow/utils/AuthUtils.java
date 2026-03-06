package com.example.mindflow.utils;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

public class AuthUtils {

    // 缓存 Token，避免每次请求都重新加密，节省 CPU
    private static final Map<String, String> tokenCache = new HashMap<>();
    private static final long EXPIRE_MILLIS = 3600 * 1000; // Token 有效期 1 小时

    public static String getToken(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) return null;

        // 简单的缓存检查（可选）
        if (tokenCache.containsKey(apiKey)) {
            // 这里为了简单，暂不检查过期时间，实际生产环境应该检查
            return tokenCache.get(apiKey);
        }

        try {
            String[] parts = apiKey.split("\\.");
            if (parts.length != 2) return null;

            String id = parts[0];
            String secret = parts[1];

            return generateJwt(id, secret);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String generateJwt(String id, String secret) throws Exception {
        long timestamp = System.currentTimeMillis();

        // 1. Header
        JSONObject header = new JSONObject();
        header.put("alg", "HS256");
        header.put("sign_type", "SIGN");

        // 2. Payload
        JSONObject payload = new JSONObject();
        payload.put("api_key", id);
        payload.put("exp", timestamp + EXPIRE_MILLIS);
        payload.put("timestamp", timestamp);

        // 3. Base64 编码
        String encodedHeader = Base64.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String encodedPayload = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

        // 4. 签名
        String contentToSign = encodedHeader + "." + encodedPayload;
        String signature = hmacSha256(contentToSign, secret);

        // 5. 拼接 Token
        String token = encodedHeader + "." + encodedPayload + "." + signature;

        tokenCache.put(id + "." + secret, token);
        return token;
    }

    private static String hmacSha256(String data, String key) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] bytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
