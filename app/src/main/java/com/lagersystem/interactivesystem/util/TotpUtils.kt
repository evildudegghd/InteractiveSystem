package com.lagersystem.interactivesystem.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpUtils {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val SECURE_RANDOM = SecureRandom()

    fun generateSecret(length: Int = 10): String {
        val bytes = ByteArray(length)
        SECURE_RANDOM.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    fun verifyCode(secret: String, code: String): Boolean {
        if (code.length != 6) return false
        val secretBytes = decodeBase32(secret) ?: return false
        val timeStep = System.currentTimeMillis() / 1000 / 30
        
        // Check current, previous and next time step to be lenient
        for (i in -1..1) {
            if (generateTotp(secretBytes, timeStep + i) == code) {
                return true
            }
        }
        return false
    }

    fun generateOtpAuthUri(secret: String, accountName: String, issuer: String = "InteractiveSystem"): String {
        val escapedAccount = accountName.replace(" ", "%20")
        val escapedIssuer = issuer.replace(" ", "%20")
        return "otpauth://totp/$escapedIssuer:$escapedAccount?secret=$secret&issuer=$escapedIssuer&algorithm=SHA1&digits=6&period=30"
    }

    fun generateQrCode(uri: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(uri, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun generateTotp(secret: ByteArray, timeIndex: Long): String {
        val data = ByteArray(8)
        var value = timeIndex
        for (i in 7 downTo 0) {
            data[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        var truncatedHash: Long = 0
        for (i in 0..3) {
            truncatedHash = (truncatedHash shl 8) or (hash[offset + i].toInt() and 0xFF).toLong()
        }

        truncatedHash = truncatedHash and 0x7FFFFFFF
        truncatedHash %= 10.0.pow(6.0).toLong()

        return truncatedHash.toString().padStart(6, '0')
    }

    private fun encodeBase32(data: ByteArray): String {
        val sb = StringBuilder()
        var bitBuffer = 0
        var bitCount = 0
        for (b in data) {
            bitBuffer = (bitBuffer shl 8) or (b.toInt() and 0xFF)
            bitCount += 8
            while (bitCount >= 5) {
                sb.append(ALPHABET[(bitBuffer ushr (bitCount - 5)) and 0x1F])
                bitCount -= 5
            }
        }
        if (bitCount > 0) {
            sb.append(ALPHABET[(bitBuffer shl (5 - bitCount)) and 0x1F])
        }
        return sb.toString()
    }

    private fun decodeBase32(base32: String): ByteArray? {
        val out = mutableListOf<Byte>()
        var bitBuffer = 0
        var bitCount = 0
        for (c in base32.uppercase()) {
            val index = ALPHABET.indexOf(c)
            if (index == -1) continue
            bitBuffer = (bitBuffer shl 5) or index
            bitCount += 5
            if (bitCount >= 8) {
                out.add(((bitBuffer ushr (bitCount - 8)) and 0xFF).toByte())
                bitCount -= 8
            }
        }
        return out.toByteArray()
    }
}
