package com.josuedev.neomessage

import android.content.Context
import android.content.SharedPreferences

object NeoHiddenChatsManager {
    private const val PREF_NAME = "NeoHiddenChatsPrefs"
    private const val KEY_PIN = "vault_pin"
    private const val KEY_HIDDEN_PREFIX = "hidden_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isPinConfigured(context: Context): Boolean {
        val pin = getPrefs(context).getString(KEY_PIN, null)
        return !pin.isNullOrEmpty()
    }

    fun checkPin(context: Context, enteredPin: String): Boolean {
        val pin = getPrefs(context).getString(KEY_PIN, null)
        if (pin.isNullOrEmpty()) return false
        return pin == enteredPin.trim()
    }

    fun setPin(context: Context, newPin: String) {
        getPrefs(context).edit().putString(KEY_PIN, newPin.trim()).apply()
    }

    fun isChatHidden(context: Context, chatId: String): Boolean {
        return getPrefs(context).getBoolean(KEY_HIDDEN_PREFIX + chatId, false)
    }

    fun setChatHidden(context: Context, chatId: String, hidden: Boolean) {
        val sp = getPrefs(context).edit()
        if (hidden) {
            sp.putBoolean(KEY_HIDDEN_PREFIX + chatId, true)
        } else {
            sp.remove(KEY_HIDDEN_PREFIX + chatId)
        }
        sp.apply()
    }

    fun getHiddenChatIds(context: Context): Set<String> {
        val all = getPrefs(context).all
        val set = mutableSetOf<String>()
        for ((key, value) in all) {
            if (key.startsWith(KEY_HIDDEN_PREFIX) && value == true) {
                set.add(key.removePrefix(KEY_HIDDEN_PREFIX))
            }
        }
        return set
    }
}
