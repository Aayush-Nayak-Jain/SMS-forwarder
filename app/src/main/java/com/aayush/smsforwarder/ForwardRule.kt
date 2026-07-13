package com.aayush.smsforwarder

import org.json.JSONObject
import java.util.UUID

/**
 * A single watch rule: messages arriving from [senderMatch] get relayed
 * verbatim to [recipient]. [senderMatch] is compared against the incoming
 * SMS "from" address using a normalized, suffix-based match so that
 * "+91 98765 43210" and "9876543210" are treated as the same sender.
 */
data class ForwardRule(
    val id: String = UUID.randomUUID().toString(),
    var senderMatch: String,
    var senderLabel: String = senderMatch,
    var recipient: String,
    var enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("senderMatch", senderMatch)
        put("senderLabel", senderLabel)
        put("recipient", recipient)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(o: JSONObject): ForwardRule = ForwardRule(
            id = o.optString("id", UUID.randomUUID().toString()),
            senderMatch = o.getString("senderMatch"),
            senderLabel = o.optString("senderLabel", o.getString("senderMatch")),
            recipient = o.getString("recipient"),
            enabled = o.optBoolean("enabled", true)
        )
    }
}
