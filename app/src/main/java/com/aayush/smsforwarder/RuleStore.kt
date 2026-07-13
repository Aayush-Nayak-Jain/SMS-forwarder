package com.aayush.smsforwarder

import android.content.Context
import org.json.JSONArray

/**
 * Single source of truth for forwarding rules + the master on/off switch.
 * Backed by SharedPreferences (private mode) so nothing is written outside
 * app-private storage on the device.
 */
object RuleStore {

    private const val PREFS = "sms_forwarder_prefs"
    private const val KEY_RULES = "rules_json"
    private const val KEY_MASTER_ENABLED = "master_enabled"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isMasterEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_MASTER_ENABLED, false)

    fun setMasterEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
    }

    fun getRules(ctx: Context): MutableList<ForwardRule> {
        val raw = prefs(ctx).getString(KEY_RULES, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        val out = mutableListOf<ForwardRule>()
        for (i in 0 until arr.length()) {
            out.add(ForwardRule.fromJson(arr.getJSONObject(i)))
        }
        return out
    }

    fun deleteRules(ctx: Context, ruleIds: Set<String>) {
        val rules = getRules(ctx).filterNot { ruleIds.contains(it.id) }
        saveRules(ctx, rules)
    }

    fun saveRules(ctx: Context, rules: List<ForwardRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun addRule(ctx: Context, rule: ForwardRule) {
        val rules = getRules(ctx)
        rules.add(rule)
        saveRules(ctx, rules)
    }

    fun updateRule(ctx: Context, rule: ForwardRule) {
        val rules = getRules(ctx)
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) {
            rules[idx] = rule
            saveRules(ctx, rules)
        }
    }

    fun deleteRule(ctx: Context, ruleId: String) {
        val rules = getRules(ctx).filterNot { it.id == ruleId }
        saveRules(ctx, rules)
    }

    /**
     * Normalizes a phone number / sender ID for comparison: strips spaces,
     * dashes, parens, and a leading "+<countrycode>" so "+91 98765-43210"
     * and "9876543210" match. Alphanumeric sender IDs (e.g. "AD-HDFCBK")
     * pass through uppercased and unchanged.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.any { it.isLetter() }) {
            return trimmed.uppercase().replace(Regex("[\\s\\-]"), "")
        }
        val digitsOnly = trimmed.filter { it.isDigit() }
        // Keep last 10 digits so country-code prefixes don't break matching.
        return if (digitsOnly.length > 10) digitsOnly.takeLast(10) else digitsOnly
    }

    /** Finds the first enabled rule whose senderMatch matches [incomingFrom]. */
    // Common 2-letter DLT telecom-operator prefixes seen on Indian sender IDs
    // (AD-HDFCBK, VM-HDFCBK, VK-HDFCBK, BZ-HDFCBK...). Different operators
    // route the same sender through different prefixes, so an exact match
    // on the raw ID misses messages from the same source.
    private val DLT_PREFIX = Regex("^(AD|VM|VK|BW|BZ|BP|BT|JM|TM|DM|JD|TX|CP|IM|LM|JK|TZ|QP|TA|TB|VD|IX)")

    private fun coreAlphaCode(normalized: String): String = normalized.replace(DLT_PREFIX, "")

    /**
     * Finds the first enabled rule whose senderMatch matches [incomingFrom].
     * Tries, in order: exact normalized match, then — for alphanumeric
     * sender IDs only — a match with the DLT operator prefix stripped from
     * both sides, then a containment check (guarded by a minimum length so
     * short codes can't false-positive against each other).
     */
    fun findMatchingRule(ctx: Context, incomingFrom: String): ForwardRule? {
        val normalizedIncoming = normalize(incomingFrom)
        val rules = getRules(ctx).filter { it.enabled }

        rules.firstOrNull { normalize(it.senderMatch) == normalizedIncoming }?.let { return it }

        val incomingIsAlpha = normalizedIncoming.any { it.isLetter() }
        if (!incomingIsAlpha) return null // don't fuzzy-match plain phone numbers

        val incomingCore = coreAlphaCode(normalizedIncoming)
        rules.firstOrNull { coreAlphaCode(normalize(it.senderMatch)) == incomingCore }?.let { return it }

        if (incomingCore.length >= 5) {
            rules.firstOrNull {
                val ruleCore = coreAlphaCode(normalize(it.senderMatch))
                ruleCore.length >= 5 && (incomingCore.contains(ruleCore) || ruleCore.contains(incomingCore))
            }?.let { return it }
        }

        return null
    }
}
