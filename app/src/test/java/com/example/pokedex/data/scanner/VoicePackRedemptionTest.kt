package com.example.pokedex.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePackRedemptionTest {
    @Test
    fun `normalizes formatted redeem code`() {
        assertEquals("ABCDEFGHJK23MN45", normalizeVoicePackRedeemCode("abcd-efgh jk23-mn45"))
    }

    @Test
    fun `prepares valid redemption without network work`() {
        val result = prepareVoicePackRedemption("abcd-efgh-jk23-mn45")

        assertTrue(result is VoicePackRedemptionPreparation.Ready)
        assertEquals(
            VoicePackRedemptionRequest("ABCDEFGHJK23MN45"),
            (result as VoicePackRedemptionPreparation.Ready).request,
        )
    }

    @Test
    fun `rejects malformed redemption code`() {
        assertTrue(
            prepareVoicePackRedemption("too-short") is
                VoicePackRedemptionPreparation.Invalid,
        )
    }
}
