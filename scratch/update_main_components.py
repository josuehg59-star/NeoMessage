with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Update signature of ChatsListScreen to include onNavigateToCorteCaja
old_sig = """fun ChatsListScreen(
    userId: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToCommunities: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToLinkedDevices: () -> Unit,
    onNavigateToStarred: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToBroadcasts: () -> Unit,
    onNavigateToAi: () -> Unit,
    textColor: Color,
    accentGradient: Brush
) {"""

new_sig = """fun ChatsListScreen(
    userId: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToStatus: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToCommunities: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToLinkedDevices: () -> Unit,
    onNavigateToStarred: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToBroadcasts: () -> Unit,
    onNavigateToAi: () -> Unit,
    textColor: Color,
    accentGradient: Brush,
    onNavigateToCorteCaja: () -> Unit = {}
) {"""

if old_sig in code:
    code = code.replace(old_sig, new_sig)
    print("Replaced ChatsListScreen signature")

# 2. Add Corte de caja item to DropdownMenu
old_menu = """                        DropdownMenuItem(text = { Text("Mensajes destacados", color = Color.White) }, onClick = { showMenu = false; onNavigateToStarred() })"""
new_menu = """                        DropdownMenuItem(text = { Text("💰 Corte de caja y ventas", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold) }, onClick = { showMenu = false; onNavigateToCorteCaja() })
                        DropdownMenuItem(text = { Text("Mensajes destacados", color = Color.White) }, onClick = { showMenu = false; onNavigateToStarred() })"""

if old_menu in code:
    code = code.replace(old_menu, new_menu)
    print("Added Corte de caja to DropdownMenu")

# 3. Remove dumping of all contacts with 'Toca para abrir conversación'
# So that only chats with messages appear (like WhatsApp)
old_contacts_dump = """        // A) Contactos locales guardados en NeoContacts
        spContacts.all.forEach { (phone, nameObj) ->
            val cleanOther = NeoUtils.sanitizeId(phone)
            if (cleanOther.isNotEmpty() && cleanOther.takeLast(10) != myCleanId.takeLast(10)) {
                val name = nameObj?.toString() ?: cleanOther
                val chatId = NeoUtils.getChatId(myCleanId, cleanOther)
                updateEntry(Triple(name, chatId, "Toca para abrir conversación"))
            }
        }"""

new_contacts_dump = """        // A) Carga de chats archivados guardados localmente
        val spArchived = context.getSharedPreferences("NeoArchived", Context.MODE_PRIVATE)
        spArchived.all.keys.forEach { if (!archivedIds.contains(it)) archivedIds.add(it) }"""

if old_contacts_dump in code:
    code = code.replace(old_contacts_dump, new_contacts_dump)
    print("Removed unmessaged contacts dump")

# 4. In archiving action button, also save to spArchived locally
old_archive_action = """                                if (isArchived) aRef.removeValue() else aRef.setValue(true)
                                selectedChatAction = null
                                Toast.makeText(context, if (isArchived) "Chat desarchivado." else "Chat enviado a archivos.", Toast.LENGTH_SHORT).show()"""

new_archive_action = """                                val spArch = context.getSharedPreferences("NeoArchived", Context.MODE_PRIVATE)
                                if (isArchived) {
                                    aRef.removeValue()
                                    spArch.edit().remove(selectedChatAction!!.second).apply()
                                    archivedIds.remove(selectedChatAction!!.second)
                                } else {
                                    aRef.setValue(true)
                                    spArch.edit().putBoolean(selectedChatAction!!.second, true).apply()
                                    if (!archivedIds.contains(selectedChatAction!!.second)) archivedIds.add(selectedChatAction!!.second)
                                }
                                selectedChatAction = null
                                Toast.makeText(context, if (isArchived) "Chat desarchivado." else "Chat archivado 📁", Toast.LENGTH_SHORT).show()"""

if old_archive_action in code:
    code = code.replace(old_archive_action, new_archive_action)
    print("Updated local archive persistence")

with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("MainComponents.kt saved.")
