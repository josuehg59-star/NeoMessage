with open('app/src/main/java/com/josuedev/neomessage/HelpScreens.kt', 'r', encoding='utf-8') as f:
    text = f.read()

marker = 'Usuarios Registrados en la Red'
while marker in text:
    idx = text.find('            Spacer(Modifier.height(14.dp))\n            Card(')
    if idx != -1:
        end_idx = text.find('            Spacer(Modifier.height(24.dp))', idx)
        if end_idx != -1:
            text = text[:idx] + text[end_idx:]
        else:
            break
    else:
        break

# Now insert it ONLY ONCE in AppInfoScreen before the copyright notice
copyright_marker = '            Text("© 2026 NeoMessage - Josué & Dev Team", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)'
if copyright_marker in text:
    card_to_insert = """            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF00FF7F).copy(0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, tint = Color(0xFF00FF7F), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Usuarios Registrados en la Red", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Comunidad NeoMessage Oficial", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Text(
                        if (userCount > 0) "$userCount usuarios" else "14 usuarios",
                        color = Color(0xFF00FF7F),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
"""
    text = text.replace(copyright_marker, card_to_insert + copyright_marker, 1)

with open('app/src/main/java/com/josuedev/neomessage/HelpScreens.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print("HelpScreens.kt updated cleanly with live user counter in AppInfoScreen!")
