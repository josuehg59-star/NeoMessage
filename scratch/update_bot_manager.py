path = 'app/src/main/java/com/josuedev/neomessage/NeoBotManager.kt'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('val isEnabled: Boolean = true,', 'val isEnabled: Boolean = false,')

old_is_enabled = """    fun isBotEnabled(context: Context): Boolean {
        val sp = getPrefs(context)
        if (sp.contains("bot_active_switch")) {
            return sp.getBoolean("bot_active_switch", true)
        }
        return true
    }"""

new_is_enabled = """    fun isBotEnabled(context: Context): Boolean {
        val sp = getPrefs(context)
        return sp.getBoolean("bot_active_switch", false) // DESACTIVADO EN AUTOMATICO
    }"""

text = text.replace(old_is_enabled, new_is_enabled)

old_def_cfg = """            val defaultConfig = BotConfig(
                isEnabled = true,"""

new_def_cfg = """            val defaultConfig = BotConfig(
                isEnabled = false,"""

text = text.replace(old_def_cfg, new_def_cfg)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

print("NeoBotManager.kt disabled by default successfully")
