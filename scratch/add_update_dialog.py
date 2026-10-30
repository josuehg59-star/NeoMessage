with open('app/src/main/java/com/josuedev/neomessage/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. State for update dialog
target_states = "    var currentRingtone by remember { mutableStateOf<android.media.Ringtone?>(null) }"
new_states = """    var currentRingtone by remember { mutableStateOf<android.media.Ringtone?>(null) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        NeoUpdateManager.checkForUpdates(context) { info ->
            availableUpdate = info
        }
    }"""

if target_states in code:
    code = code.replace(target_states, new_states, 1)
    print("Added update check state")

# 2. Add dialog rendering before end of setContent
target_dialog_end = "        // --- DIÁLOGO DE TEMAS MAESTRO ---"
new_dialog_code = """        // --- DIÁLOGO DE ACTUALIZACIÓN AUTOMÁTICA (OTA) ---
        if (availableUpdate != null) {
            val upd = availableUpdate!!
            AlertDialog(
                onDismissRequest = { if (!upd.isMandatory) availableUpdate = null },
                containerColor = Color(0xFF0F172A),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Actualización Disponible", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nueva versión: v${upd.versionName}", color = Color(0xFF00FF7F), fontWeight = FontWeight.Bold)
                        Text(upd.changelog, color = Color.LightGray, fontSize = 13.sp)
                        Text("La descarga se realiza sin perder tus chats ni configuraciones.", color = Color.Gray, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            NeoUpdateManager.downloadAndInstallApk(context, upd.apkUrl)
                            availableUpdate = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))
                    ) {
                        Text("ACTUALIZAR AHORA", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    if (!upd.isMandatory) {
                        TextButton(onClick = { availableUpdate = null }) {
                            Text("Más tarde", color = Color.Gray)
                        }
                    }
                }
            )
        }

        // --- DIÁLOGO DE TEMAS MAESTRO ---"""

if target_dialog_end in code:
    code = code.replace(target_dialog_end, new_dialog_code, 1)
    print("Added update dialog rendering")

with open('app/src/main/java/com/josuedev/neomessage/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("Saved MainActivity.kt with OTA update support")
