package com.example.ghtrending.data.translation

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯云 API 3.0 签名方法 v3（TC3-HMAC-SHA256）。
 *
 * 文档：https://cloud.tencent.com/document/api/213/30654
 *
 * 1. 拼接规范请求串 CanonicalRequest
 * 2. 拼接待签名字符串 StringToSign
 * 3. 派生签名密钥并计算 Signature
 * 4. 拼接 Authorization
 */
internal object Tc3Signer {

    const val ALGORITHM = "TC3-HMAC-SHA256"
    private const val SIGNED_HEADERS = "content-type;host"
    private const val TERMINATOR = "tc3_request"

    /**
     * @param contentType 必须与实际发送请求的 Content-Type 头完全一致
     * @param payload 必须与实际发送的请求体字节（UTF-8）完全一致
     * @param timestamp 秒级 Unix 时间戳，需与 X-TC-Timestamp 头一致
     */
    fun authorization(
        secretId: String,
        secretKey: String,
        service: String,
        host: String,
        contentType: String,
        payload: String,
        timestamp: Long,
    ): String {
        // 日期必须为 UTC 时区，且与 timestamp 对应
        val date = Instant.ofEpochSecond(timestamp).atOffset(ZoneOffset.UTC).toLocalDate().toString()

        // ************* 步骤 1：拼接规范请求串 *************
        val httpRequestMethod = "POST"
        val canonicalUri = "/"
        val canonicalQueryString = ""
        val canonicalHeaders = "content-type:${contentType.trim().lowercase()}\n" +
            "host:${host.trim().lowercase()}\n"
        val hashedRequestPayload = sha256Hex(payload)
        val canonicalRequest = httpRequestMethod + "\n" +
            canonicalUri + "\n" +
            canonicalQueryString + "\n" +
            canonicalHeaders + "\n" +
            SIGNED_HEADERS + "\n" +
            hashedRequestPayload

        // ************* 步骤 2：拼接待签名字符串 *************
        val credentialScope = "$date/$service/$TERMINATOR"
        val hashedCanonicalRequest = sha256Hex(canonicalRequest)
        val stringToSign = ALGORITHM + "\n" +
            timestamp + "\n" +
            credentialScope + "\n" +
            hashedCanonicalRequest

        // ************* 步骤 3：计算签名 *************
        val secretDate = hmacSha256(("TC3$secretKey").toByteArray(Charsets.UTF_8), date)
        val secretService = hmacSha256(secretDate, service)
        val secretSigning = hmacSha256(secretService, TERMINATOR)
        val signature = hmacSha256(secretSigning, stringToSign).toHex()

        // ************* 步骤 4：拼接 Authorization *************
        return "$ALGORITHM " +
            "Credential=$secretId/$credentialScope, " +
            "SignedHeaders=$SIGNED_HEADERS, " +
            "Signature=$signature"
    }

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .toHex()

    fun hmacSha256(key: ByteArray, message: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            result.append(hexChars[value ushr 4])
            result.append(hexChars[value and 0x0F])
        }
        return result.toString()
    }
}
