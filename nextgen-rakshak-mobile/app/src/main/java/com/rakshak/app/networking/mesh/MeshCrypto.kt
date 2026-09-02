package com.rakshak.app.networking.mesh

import com.rakshak.app.BuildConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 authentication for mesh packets (synopsis §5.1.4 — "alert payloads
 * are additionally signed so a relaying device cannot tamper with alert
 * content").
 *
 * The MAC is computed over the packet from byte 1 to the end of the body, i.e.
 * everything **except** byte 0 (the TTL). The TTL is decremented in place at
 * every relay ([MeshPayloadCodec.withDecrementedTtl]); excluding it lets a
 * relayed packet keep a valid MAC without re-signing, while still protecting the
 * message id, type and every payload field.
 *
 * The key is embedded in the build ([BuildConfig.MESH_HMAC_KEY], overridable in
 * `local.properties`). This is a shared secret, not a per-alert signature: it
 * stops packets from a device **not running this build** and catches corruption,
 * which is what the relay-tampering threat needs. It does not defend against a
 * modified copy of the official app — that would need the kiosk to sign each
 * alert with a private key, which is out of scope and noted as future work.
 */
object MeshCrypto {

    private const val ALGO = "HmacSHA256"

    /** Length of the MAC appended to every packet. */
    const val MAC_LEN = 32

    private val keySpec by lazy {
        SecretKeySpec(BuildConfig.MESH_HMAC_KEY.toByteArray(Charsets.UTF_8), ALGO)
    }

    /** MAC over `data[from until end]`. */
    fun mac(data: ByteArray, from: Int = 0, end: Int = data.size): ByteArray =
        Mac.getInstance(ALGO).apply { init(keySpec) }.doFinal(data.copyOfRange(from, end))

    /**
     * @return true if [packet] ends in a valid MAC over `packet[1 until len-32]`.
     * Constant-time comparison so a caller cannot time-probe the expected MAC.
     */
    fun verify(packet: ByteArray): Boolean {
        if (packet.size < MAC_LEN + 2) return false
        val macStart = packet.size - MAC_LEN
        val expected = mac(packet, from = 1, end = macStart)
        var diff = 0
        for (i in 0 until MAC_LEN) diff = diff or (expected[i].toInt() xor packet[macStart + i].toInt())
        return diff == 0
    }
}
