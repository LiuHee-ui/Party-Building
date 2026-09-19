package com.hongmai.common.crypto;

import com.hongmai.common.exception.BizException;
import com.hongmai.common.exception.ErrorCode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加解密（承接 N2）。
 * AES/GCM/NoPadding，随机 12 字节 IV 前置到密文，输出 Base64。
 * 密钥由启动配置注入，未初始化时调用即抛异常——避免静默使用空密钥。
 */
public final class CryptoUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile byte[] key;

    private CryptoUtil() {
    }

    /** 由 party-boot 的配置类在启动时调用。keyBase64 必须是 16/24/32 字节的 Base64。 */
    public static void initKey(String keyBase64) {
        byte[] decoded = Base64.getDecoder().decode(keyBase64);
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            throw new IllegalStateException("加密密钥长度非法，必须为 16/24/32 字节");
        }
        key = decoded;
    }

    /** 仅用于测试环境重置密钥。 */
    public static void resetKey() {
        key = null;
    }

    public static byte[] encryptBytes(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);
            return result;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "敏感字段加密失败");
        }
    }

    public static String decryptBytes(byte[] cipherBytes) {
        if (cipherBytes == null) {
            return null;
        }
        if (cipherBytes.length <= IV_LENGTH) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "密文长度非法");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(cipherBytes, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[cipherBytes.length - IV_LENGTH];
            System.arraycopy(cipherBytes, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "敏感字段解密失败");
        }
    }

    public static String encryptToBase64(String plainText) {
        byte[] bytes = encryptBytes(plainText);
        return bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
    }

    public static String decryptFromBase64(String base64) {
        if (base64 == null) {
            return null;
        }
        return decryptBytes(Base64.getDecoder().decode(base64));
    }

    private static SecretKeySpec secretKey() {
        byte[] current = key;
        if (current == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "加密密钥未初始化");
        }
        return new SecretKeySpec(current, "AES");
    }

    /**
     * 确定性哈希，用于「同一明文能否查到已存在记录」的唯一性校验。
     *
     * 为什么不能用 encryptBytes 做唯一性校验：GCM 每次生成随机 IV，
     * 同一手机号的密文每次都不同，WHERE mobile_cipher = ? 永远查不到 —— 这是真实的坑。
     *
     * 为什么用 HMAC 而不是裸 SHA-256：手机号只有 11 位，裸 SHA-256 可以被彩虹表
     * 或暴力枚举反推。HMAC 用密钥做盐，拿到数据库也无法离线枚举。
     */
    public static String deterministicHash(String plainText) {
        if (plainText == null) {
            return null;
        }
        byte[] current = key;
        if (current == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "加密密钥未初始化");
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(current, "HmacSHA256"));
            byte[] digest = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "哈希计算失败");
        }
    }
}
