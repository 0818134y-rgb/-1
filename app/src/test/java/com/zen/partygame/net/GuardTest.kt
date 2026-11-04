package com.zen.partygame.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
class GuardTest {
    // 本物の管理者の鍵で、ブラウザ（WebCrypto）と同じ「r と s を並べた64バイト」の形で作った署名
    private val ts = 1793781649780L
    private val proof = AdminProof(ts, "abc123", "i5B6ZOXiieVsDDmVwlMLn86M3or8WxXu3+ocHQaz7xZg9sjeB4TU1TqJq3aN7dc2H6u8o8Tz6gp03Pc0y7Ce3w==")

    @Test fun acceptsRealAdminSignature() {
        assertTrue(Guard.verifyAdmin("123456", "zpga-test", proof, now = ts + 1000))
    }

    @Test fun rejectsTamperedOrExpired() {
        assertFalse(Guard.verifyAdmin("654321", "zpga-test", proof, now = ts))          // 別の部屋
        assertFalse(Guard.verifyAdmin("123456", "zpga-other", proof, now = ts))         // 別の接続ID
        assertFalse(Guard.verifyAdmin("123456", "zpga-test", proof.copy(nonce = "x"), now = ts))
        assertFalse(Guard.verifyAdmin("123456", "zpga-test", proof, now = ts + 11 * 60_000L))  // 古い
        assertFalse(Guard.verifyAdmin("123456", "zpga-test", null, now = ts))
        assertFalse(Guard.verifyAdmin("123456", "zpga-test", proof.copy(sig = "AAAA"), now = ts))
    }

    @Test fun derAndRawRoundTrip() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        repeat(20) { i ->
            val s = Signature.getInstance("SHA256withECDSA"); s.initSign(kp.private); s.update("m$i".toByteArray())
            val der = s.sign()
            val raw = Guard.derToRaw(der)
            assertEquals(64, raw.size)
            val v = Signature.getInstance("SHA256withECDSA"); v.initVerify(kp.public); v.update("m$i".toByteArray())
            assertTrue(v.verify(Guard.rawToDer(raw)))
            assertArrayEquals(raw, Guard.derToRaw(Guard.rawToDer(raw)))
        }
    }

    @Test fun deviceHash() {
        assertEquals("", Guard.hash(""))
        assertEquals(64, Guard.hash("abc").length)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Guard.hash("abc"))
    }
}
