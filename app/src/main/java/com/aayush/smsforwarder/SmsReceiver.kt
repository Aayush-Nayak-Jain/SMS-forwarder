package com.aayush.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!RuleStore.isMasterEnabled(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // A single SMS can arrive as multiple PDU parts; concatenate the body
        // before matching/forwarding.
        val sender = messages[0].originatingAddress ?: return
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

        val rule = RuleStore.findMatchingRule(context, sender) ?: run {
            Log.i(TAG, "No rule matched sender \"$sender\" — ignoring")
            return
        }

        // Dual-SIM devices deliver this extra so we can send the forward back
        // out on the SAME SIM that received it, instead of whatever SIM
        // happens to be the phone's default for sending. Sending on the
        // wrong SIM is the most common reason forwards silently fail.
        val subscriptionId = intent.getIntExtra("subscription", -1).takeIf { it != -1 }
            ?: intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1).takeIf { it != -1 }

        // goAsync() tells Android "I'm not done yet, don't recycle this
        // receiver instance" — gives us headroom to send on a background
        // thread instead of racing the ~10s main-thread receiver window.
        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                forward(context, rule, sender, fullBody, subscriptionId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun forward(
        context: Context,
        rule: ForwardRule,
        originalSender: String,
        body: String,
        subscriptionId: Int?
    ) {
        try {
            val smsManager = getSmsManagerForSubscription(context, subscriptionId)
            val outgoing = "Fwd from $originalSender: $body"
            val parts = smsManager.divideMessage(outgoing)
            smsManager.sendMultipartTextMessage(rule.recipient, null, parts, null, null)
            Log.i(TAG, "Forwarded SMS from $originalSender to ${rule.recipient} (subId=$subscriptionId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to forward SMS from $originalSender to ${rule.recipient}", e)
        }
    }

    /**
     * Resolves the SmsManager tied to the SIM the message arrived on, so
     * forwards go out over the same line. Falls back to the device default
     * if the subscription id is missing or the API level doesn't support it.
     */
    private fun getSmsManagerForSubscription(context: Context, subscriptionId: Int?): SmsManager {
        if (subscriptionId != null && subscriptionId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return try {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } catch (e: Exception) {
                SmsManager.getDefault()
            }
        }
        return SmsManager.getDefault()
    }

    companion object {
        private const val TAG = "SmsForwarder"
        // Single background thread is enough for personal SMS volumes and
        // keeps forwards in arrival order.
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}