package com.rakshak.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * One SIM currently inserted in the device, as the profile screen shows it.
 *
 * [number] is nullable on purpose and is nullable *often*: plenty of carriers and
 * OEMs never populate the subscriber's own MSISDN, even with every relevant
 * permission granted. A card with a null number is still a perfectly valid,
 * selectable SIM — the volunteer just has to type the number once.
 */
data class SimCard(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val label: String,
    val number: String?,
    val isDefaultVoice: Boolean,
) {
    /** "SIM 1 · Jio" — what identifies a card to the volunteer, not the subscription id. */
    val displayName: String
        get() {
            val slot = if (slotIndex >= 0) "SIM ${slotIndex + 1}" else "SIM"
            // Carrier first: it is the name the volunteer reads off their own
            // status bar. The OEM label is a fallback because on many phones it
            // is only ever the slot restated ("SIM1"), which would render as the
            // useless "SIM 1 · SIM1" — so that case drops back to the slot alone.
            val name = carrierName.ifBlank { label }.trim()
            val restatesSlot = name.replace(" ", "").equals(slot.replace(" ", ""), ignoreCase = true)
            return if (name.isBlank() || restatesSlot) slot else "$slot · $name"
        }
}

/**
 * Best-effort read of the device's own SIM phone numbers, so a volunteer never
 * has to type one in.
 *
 * Two related jobs:
 *  - [listSims] enumerates every inserted SIM so the profile screen can let a
 *    dual-SIM volunteer pick which line an officer should call back on;
 *  - [primaryNumber] / [numberFor] read the MSISDN, auto-picking the default
 *    voice line when nobody has chosen one.
 *
 * "Best-effort" is load-bearing here: every accessor below can legitimately
 * return null/false, and callers must treat that as "unknown", never as "this
 * device has no SIM".
 */
object SimPhoneProvider {

    const val NO_SUBSCRIPTION = SubscriptionManager.INVALID_SUBSCRIPTION_ID

    fun hasPermission(context: Context): Boolean {
        val phoneState = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val phoneNumbers = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_PHONE_NUMBERS,
            ) == PackageManager.PERMISSION_GRANTED
            return phoneState || phoneNumbers
        }
        return phoneState
    }

    /**
     * True if at least one SIM is inserted and active, independent of whether a
     * number could be read from it. Falls back to the single-SIM signal (which
     * needs no permission) when [hasPermission] is false, rather than reporting
     * "no SIM" for a device this call simply isn't allowed to fully inspect.
     */
    fun simPresent(context: Context): Boolean {
        if (!hasPermission(context)) {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            return telephony?.simState == TelephonyManager.SIM_STATE_READY
        }
        return runCatching {
            val subscriptions = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager
            val active = subscriptions?.activeSubscriptionInfoList
            active.isNullOrEmpty().not()
        }.getOrDefault(false)
    }

    /**
     * Every inserted SIM, default voice line first. Empty when no SIM is present
     * *or* when the permission to enumerate them was refused — [simPresent] is
     * what distinguishes those two, and the UI says so.
     */
    fun listSims(context: Context): List<SimCard> {
        if (!hasPermission(context)) return emptyList()
        return runCatching {
            val subscriptions = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager
            val active = subscriptions?.activeSubscriptionInfoList.orEmpty()
            if (active.isEmpty()) return@runCatching emptyList()

            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val preferredId = defaultVoiceSubscriptionId()

            active
                .map { sub ->
                    SimCard(
                        subscriptionId = sub.subscriptionId,
                        slotIndex = sub.simSlotIndex,
                        carrierName = sub.carrierName?.toString().orEmpty(),
                        label = sub.displayName?.toString().orEmpty(),
                        number = readNumber(telephony, sub),
                        isDefaultVoice = sub.subscriptionId == preferredId,
                    )
                }
                // Default voice line first, then physical slot order, so the
                // list reads the same way the system settings screen does.
                .sortedWith(compareByDescending<SimCard> { it.isDefaultVoice }.thenBy { it.slotIndex })
        }.getOrDefault(emptyList())
    }

    /** The number on one specific SIM. Null if that subscription is gone or unreadable. */
    fun numberFor(context: Context, subscriptionId: Int): String? {
        if (subscriptionId == NO_SUBSCRIPTION) return null
        return listSims(context).firstOrNull { it.subscriptionId == subscriptionId }?.number
    }

    /**
     * The device's own number, auto-picked on dual-SIM: the default voice line if
     * it reports one, otherwise whichever SIM does. Null if unreadable for any
     * reason.
     */
    fun primaryNumber(context: Context): String? =
        listSims(context).firstNotNullOfOrNull { it.number }

    /** Subscription id of the SIM to prefer when the volunteer has not chosen one. */
    fun preferredSubscriptionId(context: Context): Int {
        val sims = listSims(context)
        val withNumber = sims.firstOrNull { it.number != null }
        return (withNumber ?: sims.firstOrNull())?.subscriptionId ?: NO_SUBSCRIPTION
    }

    private fun defaultVoiceSubscriptionId(): Int =
        runCatching { SubscriptionManager.getDefaultVoiceSubscriptionId() }
            .getOrDefault(NO_SUBSCRIPTION)

    @Suppress("DEPRECATION")
    private fun readNumber(telephony: TelephonyManager?, sub: SubscriptionInfo): String? {
        val perSubscription = runCatching { telephony?.createForSubscriptionId(sub.subscriptionId) }.getOrNull()
        val fromTelephonyManager = runCatching { perSubscription?.line1Number }.getOrNull()
        if (!fromTelephonyManager.isNullOrBlank()) return fromTelephonyManager.trim()
        // SubscriptionInfo.number is deprecated in favour of
        // SubscriptionManager.getPhoneNumber(subId) (API 33, effectively
        // carrier-privileged only) — kept as a fallback since it still works on
        // plenty of devices below that, and this whole path is best-effort.
        return runCatching { sub.number }.getOrNull()?.trim()?.takeIf(String::isNotBlank)
    }
}
