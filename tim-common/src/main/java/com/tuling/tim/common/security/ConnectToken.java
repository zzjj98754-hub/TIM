package com.tuling.tim.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Short-lived gateway-to-Netty connection credential. */
public final class ConnectToken {
    private static final String HMAC = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ConnectToken() { }

    public static String issue(long userId, String serverId, long ttlMillis, String secret) {
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        String nonce = Long.toUnsignedString(RANDOM.nextLong(), 36);
        String payload = userId + "|" + serverId + "|" + expiresAt + "|" + nonce;
        return encode(payload) + "." + encode(sign(payload, secret));
    }

    public static Claims verify(String token, String expectedServerId, String secret, long now) {
        if (token == null || secret == null || secret.isBlank()) throw new IllegalArgumentException("missing connect token");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) throw new IllegalArgumentException("malformed connect token");
        String payload = decode(parts[0]);
        byte[] expected = sign(payload, secret);
        byte[] actual = decodeBytes(parts[1]);
        if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException("invalid connect token signature");
        String[] values = payload.split("\\|", -1);
        if (values.length != 4) throw new IllegalArgumentException("malformed connect token payload");
        long userId = Long.parseLong(values[0]);
        long expiresAt = Long.parseLong(values[2]);
        if (userId <= 0 || !values[1].equals(expectedServerId)) throw new IllegalArgumentException("connect token audience mismatch");
        if (expiresAt <= now) throw new IllegalArgumentException("connect token expired");
        return new Claims(userId, values[1], expiresAt, values[3]);
    }

    private static byte[] sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("cannot sign connect token", e); }
    }
    private static String encode(String value) { return encode(value.getBytes(StandardCharsets.UTF_8)); }
    private static String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static String decode(String value) { return new String(decodeBytes(value), StandardCharsets.UTF_8); }
    private static byte[] decodeBytes(String value) { return Base64.getUrlDecoder().decode(value); }

    public record Claims(long userId, String serverId, long expiresAt, String nonce) { }
}
