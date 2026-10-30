path = r'app/src/main/java/com/josuedev/neomessage/SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

old = 'SettingsItem(Icons.Default.BusinessCenter, "Herramientas de Operador", "Perfil, métricas y automatización", onNavigateToBusiness)'
new = 'SettingsItem(Icons.Default.SmartToy, "NeoBot & IA de Ventas", "Chatbot automático, catálogo y respuestas ($0 costo)", onNavigateToBusiness)'

if old in c:
    c = c.replace(old, new)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)
    print("SUCCESS")
else:
    print("NOT FOUND")
