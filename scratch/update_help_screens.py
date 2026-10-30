with open('app/src/main/java/com/josuedev/neomessage/HelpScreens.kt', 'r', encoding='utf-8') as f:
    code = f.read()

old_sig = "fun BusinessToolsScreen(onBack: () -> Unit) {"
new_sig = "fun BusinessToolsScreen(onBack: () -> Unit, onNavigateToCorteCaja: () -> Unit = {}) {"
if old_sig in code:
    code = code.replace(old_sig, new_sig)
    print("Replaced signature")

# Search for Inventory2 line
lines = code.split("\n")
for i, line in enumerate(lines):
    if "Icons.Default.Inventory2" in line:
        lines[i] = """        item { SettingsRow(Icons.Default.Inventory2, "Catálogo de productos", "Explora artículos y precios", onNavigateToCorteCaja) }
        item { SettingsRow(Icons.Default.AccountBalanceWallet, "Corte de caja y ventas", "Balance diario, tickets y ganancias", onNavigateToCorteCaja) }"""
        print(f"Replaced at line {i+1}")
        break

code = "\n".join(lines)
with open('app/src/main/java/com/josuedev/neomessage/HelpScreens.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("Saved HelpScreens.kt")
