path = r'app/src/main/java/com/josuedev/neomessage/NeoAiScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

old_call = 'val response = getAiResponse(messages.last { it.second }.first)'
new_call = 'val response = getAiResponse(context, messages.last { it.second }.first)'

old_def = '''fun getAiResponse(input: String): String {
    val lower = input.lowercase()
    return when {
        lower.contains("quien eres") || lower.contains("quién eres") -> "Soy NeoMessage AI, tu asistente inteligente diseñado para ayudarte a gestionar tus mensajes, organizar tu día y responder tus dudas en la red NeoMessage."
        lower.contains("seguridad") -> "NeoMessage utiliza cifrado de grado militar AES-256 de extremo a extremo. Nadie, ni siquiera yo, puede leer tus chats privados."
        lower.contains("hola") -> "¡Hola! Un gusto saludarte. ¿Cómo va tu experiencia en la red NeoMessage hoy?"
        lower.contains("creador") || lower.contains("dueño") -> "Fui desarrollado como parte del protocolo de inteligencia de NeoMessage para Josué Dev."
        else -> "Lo entiendo. Estoy procesando tu solicitud: '$input'. ¿En qué más te puedo servir para mejorar tu fluidez táctica en la red?"
    }
}'''

new_def = '''fun getAiResponse(context: android.content.Context, input: String): String {
    val lower = input.lowercase()
    return when {
        lower.contains("quien eres") || lower.contains("quién eres") -> "🤖 Soy NeoMessage AI, tu asistente inteligente y motor de ventas táctico de NeoMessage. Estoy disponible 24/7 sin ningún costo."
        lower.contains("seguridad") -> "🛡️ NeoMessage utiliza cifrado de grado militar AES-256 de extremo a extremo. Nadie, ni siquiera yo, puede leer tus chats privados."
        lower.contains("creador") || lower.contains("dueño") -> "🚀 Fui desarrollado como parte del protocolo de inteligencia de NeoMessage para Josué Dev."
        lower.contains("catalogo") || lower.contains("catálogo") || lower.contains("precio") || lower.contains("ubicacion") || lower.contains("horario") || lower.contains("tienen") -> {
            NeoBotManager.processMessage(context, input)
        }
        else -> {
            val botReply = NeoBotManager.processMessage(context, input)
            if (!botReply.contains("no encontré coincidencia exacta")) {
                botReply
            } else {
                "🤖 Estoy procesando tu solicitud: '$input'. Puedes preguntarme por productos, precios, horarios, o soporte de la red NeoMessage."
            }
        }
    }
}'''

if old_call in c and old_def in c:
    c = c.replace(old_call, new_call).replace(old_def, new_def)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)
    print("SUCCESS")
else:
    print("NOT FOUND - checking parts")
    print("old_call in c:", old_call in c)
    print("old_def in c:", old_def in c)
