with open('app/src/main/java/com/josuedev/neomessage/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

target = "onNavigateToChat = { name, id -> state = state.copy(activeChatName = name, activeChatId = id, currentScreen = Screen.ChatRoom) }"
replacement = """onNavigateToChat = { name, id ->
                            if (id == "neo_ai_chat") {
                                state = state.copy(currentScreen = Screen.NeoAI)
                            } else {
                                state = state.copy(activeChatName = name, activeChatId = id, currentScreen = Screen.ChatRoom)
                            }
                        }"""

if target in code:
    code = code.replace(target, replacement, 1)
    print("Replaced ChatsList onNavigateToChat")

with open('app/src/main/java/com/josuedev/neomessage/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("Saved MainActivity.kt")
