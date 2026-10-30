with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'r', encoding='utf-8') as f:
    code = f.read()

target = 'updateEntry(Triple("🤖 NeoBot Ventas (Asistente)", "private_${myCleanId}_neobot", "📦 Escribe Catalogo, Ubicacion o productos..."))'
replacement = """updateEntry(Triple("🤖 NeoBot Ventas (Asistente)", "private_${myCleanId}_neobot", "📦 Catálogo, Carrito y Corte de caja"))
        updateEntry(Triple("✨ Neo AI (Investigación y Tareas)", "neo_ai_chat", "🧠 Pregúntame tareas, ciencia, historia..."))"""

if target in code:
    code = code.replace(target, replacement)
    print("Pinned Neo AI chat added successfully")

with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("Saved MainComponents.kt")
