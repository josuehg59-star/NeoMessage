with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'r', encoding='utf-8') as f:
    text = f.read()

target = """                dismissButton = {
                    TextButton(onClick = { selectedChatAction = null }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }
        
        Column(Modifier.padding(padding).fillMaxSize()) {"""

dialogs = """                dismissButton = {
                    TextButton(onClick = { selectedChatAction = null }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }

        if (showSetPinDialog) {
            AlertDialog(
                onDismissRequest = { showSetPinDialog = false },
                containerColor = NeoTheme.Card,
                title = { Text("🔒 Crear PIN de Chats Ocultos", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Elige un PIN secreto de 4 dígitos para proteger tus chats privados. Solo tú podrás verlos.", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = hiddenPinInput,
                            onValueChange = { if (it.length <= 6) hiddenPinInput = it },
                            label = { Text("PIN Secreto (ej. 1234)") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeoTheme.Green)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (hiddenPinInput.length >= 4) {
                                NeoHiddenChatsManager.setPin(context, hiddenPinInput)
                                if (pendingHiddenChatId != null) {
                                    NeoHiddenChatsManager.setChatHidden(context, pendingHiddenChatId!!, true)
                                    pendingHiddenChatId = null
                                }
                                hiddenChatUpdateTrigger++
                                showSetPinDialog = false
                                hiddenPinInput = ""
                                Toast.makeText(context, "🔒 PIN configurado y chat ocultado con éxito.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "El PIN debe tener al menos 4 dígitos.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                    ) {
                        Text("GUARDAR PIN", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSetPinDialog = false; pendingHiddenChatId = null }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }

        if (showVaultPinDialog) {
            AlertDialog(
                onDismissRequest = { showVaultPinDialog = false },
                containerColor = NeoTheme.Card,
                title = { Text("🔒 Bóveda de Chats Ocultos", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Ingresa tu PIN secreto para desbloquear la lista de chats ocultos.", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = vaultPinInput,
                            onValueChange = { if (it.length <= 6) vaultPinInput = it },
                            label = { Text("PIN Secreto") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, focusedBorderColor = NeoTheme.Green)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (NeoHiddenChatsManager.checkPin(context, vaultPinInput)) {
                                isVaultUnlocked = true
                                showVaultPinDialog = false
                                vaultPinInput = ""
                                Toast.makeText(context, "🔓 Bóveda desbloqueada.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "❌ PIN incorrecto.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeoTheme.Green)
                    ) {
                        Text("DESBLOQUEAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVaultPinDialog = false; vaultPinInput = "" }) { Text("CANCELAR", color = Color.Gray) }
                }
            )
        }
        
        Column(Modifier.padding(padding).fillMaxSize()) {"""

if target in text:
    text = text.replace(target, dialogs, 1)
    with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'w', encoding='utf-8') as f:
        f.write(text)
    print("Vault dialogs inserted exactly once successfully!")
else:
    print("Warning: target not found in MainComponents.kt")
