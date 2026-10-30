with open('app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Add showCommerceSheet state and TopBar action icon
old_actions = """                    IconButton(onClick = { onNavigateToCalling(otherCleanId) }) { 
                        Icon(Icons.Filled.Call, "Llamada de voz", tint = Color(0xFF00FF7F)) 
                    }"""

new_actions = """                    IconButton(onClick = { onNavigateToCalling(otherCleanId) }) { 
                        Icon(Icons.Filled.Call, "Llamada de voz", tint = Color(0xFF00FF7F)) 
                    }
                    var showCommerceSheet by remember { mutableStateOf(false) }
                    IconButton(onClick = { showCommerceSheet = true }) { 
                        Icon(Icons.Filled.ShoppingCart, "Catálogo y Carrito", tint = Color(0xFFFFD700)) 
                    }"""

if old_actions in code:
    code = code.replace(old_actions, new_actions)
    print("Added shopping cart action icon in TopBar")

# 2. Add CommerceCartSheet display at line 966
target_end = """            dismissButton = {
                TextButton(onClick = { selectedMessage = null }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }
}"""

replacement_end = """            dismissButton = {
                TextButton(onClick = { selectedMessage = null }) { Text("CANCELAR", color = Color.Gray) }
            }
        )
    }

    if (showCommerceSheet) {
        CommerceCartSheet(
            chatId = chatId,
            onDismiss = { showCommerceSheet = false },
            onOrderPlaced = { receipt ->
                sendMessage(receipt)
            }
        )
    }
}"""

if target_end in code:
    code = code.replace(target_end, replacement_end)
    print("Added CommerceCartSheet at bottom of ChatRoomScreen")

with open('app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("ChatRoomScreen.kt updated!")
