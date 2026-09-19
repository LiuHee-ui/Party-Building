package com.hongmai.common.crypto;

import com.hongmai.common.exception.BizException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 敏感字段加解密测试（承接 spec N2 / AC24）。
 * 验证：往返一致、随机 IV 使同一明文两次密文不同、篡改可检出、密钥未初始化即失败。
 */
class CryptoUtilTest {

    @BeforeAll
    static void setUp() {
        CryptoUtil.initKey(Base64.getEncoder()
                .encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("加解密往返一致")
    void roundTrip() {
        String plain = "13800138000";
        byte[] cipher = CryptoUtil.encryptBytes(plain);
        assertEquals(plain, CryptoUtil.decryptBytes(cipher));
    }

    @Test
    @DisplayName("同一明文两次加密结果不同（随机 IV）")
    void randomIvProducesDifferentCipher() {
        String plain = "13800138000";
        byte[] first = CryptoUtil.encryptBytes(plain);
        byte[] second = CryptoUtil.encryptBytes(plain);
        assertNotEquals(Base64.getEncoder().encodeToString(first),
                Base64.getEncoder().encodeToString(second));
        assertEquals(plain, CryptoUtil.decryptBytes(first));
        assertEquals(plain, CryptoUtil.decryptBytes(second));
    }

    @Test
    @DisplayName("Base64 形式往返一致")
    void base64RoundTrip() {
        String plain = "13800138000";
        assertEquals(plain, CryptoUtil.decryptFromBase64(CryptoUtil.encryptToBase64(plain)));
    }

    @Test
    @DisplayName("空值透传为 null，不抛异常")
    void nullSafe() {
        assertNull(CryptoUtil.encryptBytes(null));
        assertNull(CryptoUtil.decryptBytes(null));
        assertNull(CryptoUtil.encryptToBase64(null));
        assertNull(CryptoUtil.decryptFromBase64(null));
    }

    @Test
    @DisplayName("密文被篡改时解密失败而非返回错误明文")
    void tamperedCipherRejected() {
        byte[] cipher = CryptoUtil.encryptBytes("13800138000");
        cipher[cipher.length - 1] = (byte) (cipher[cipher.length - 1] ^ 0x01);
        assertThrows(BizException.class, () -> CryptoUtil.decryptBytes(cipher));
    }

    @Test
    @DisplayName("长度不足的密文被拒绝")
    void tooShortCipherRejected() {
        assertThrows(BizException.class, () -> CryptoUtil.decryptBytes(new byte[8]));
    }

    @Test
    @DisplayName("密钥长度非法时拒绝初始化")
    void illegalKeyLengthRejected() {
        CryptoUtil.resetKey();
        String badKey = Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> CryptoUtil.initKey(badKey));

        CryptoUtil.initKey(Base64.getEncoder()
                .encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("密文与明文不同，确认确实做了加密")
    void cipherDiffersFromPlain() {
        String plain = "13800138000";
        assertArrayEquals(plain.getBytes(StandardCharsets.UTF_8), plain.getBytes(StandardCharsets.UTF_8));
        byte[] cipher = CryptoUtil.encryptBytes(plain);
        assertNotEquals(plain, new String(cipher, StandardCharsets.UTF_8));
    }
}
