package com.example.pokedex.data.scanner

data class VoicePackRedemptionRequest(
    val code: String,
)

sealed interface VoicePackRedemptionPreparation {
    data class Ready(val request: VoicePackRedemptionRequest) : VoicePackRedemptionPreparation
    data class Invalid(val message: String) : VoicePackRedemptionPreparation
}

fun normalizeVoicePackRedeemCode(value: String): String =
    value.uppercase().filterNot { it == '-' || it.isWhitespace() }

fun prepareVoicePackRedemption(
    value: String,
): VoicePackRedemptionPreparation {
    val code = normalizeVoicePackRedeemCode(value)
    if (!code.matches(Regex("[A-Z2-9]{16}"))) {
        return VoicePackRedemptionPreparation.Invalid("请输入有效的 16 位兑换码")
    }
    return VoicePackRedemptionPreparation.Ready(VoicePackRedemptionRequest(code))
}
