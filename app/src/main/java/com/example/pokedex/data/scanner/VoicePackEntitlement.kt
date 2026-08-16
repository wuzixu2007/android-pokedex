package com.example.pokedex.data.scanner

sealed interface VoicePackRedeemResult {
    data class Success(val voiceId: VoicePackId) : VoicePackRedeemResult
    data class Failure(val message: String) : VoicePackRedeemResult
}
