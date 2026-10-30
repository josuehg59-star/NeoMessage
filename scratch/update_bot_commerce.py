with open('app/src/main/java/com/josuedev/neomessage/NeoBotManager.kt', 'r', encoding='utf-8') as f:
    code = f.read()

# Update mensajeBienvenida
old_bienvenida = 'val mensajeBienvenida: String = "¡Hola! 👋 Bienvenido a *{negocio}*.\\n\\nSoy tu asistente virtual 🤖.\\n¿En qué podemos ayudarte hoy?\\n\\n📦 Escribe *Catalogo* para ver productos y precios\\n📍 Escribe *Ubicacion* para ver horarios y dirección\\n🕒 Escribe *Horario* para tiempos de atención\\n👤 Escribe *Asesor* para hablar con el encargado\\n\\n_🔍 O escribe directamente lo que buscas (ej: \\"coca\\", \\"impresiones\\", \\"aceite\\")._"'

new_bienvenida = 'val mensajeBienvenida: String = "¡Hola! 👋 Bienvenido a *{negocio}*.\\n\\nSoy tu asistente virtual 🤖.\\n¿En qué podemos ayudarte hoy?\\n\\n📦 Escribe *Catalogo* para ver productos y precios\\n🛒 Escribe *Carrito* para ver tu total de compras\\n📊 Escribe *Corte* para ver el corte de caja del día\\n📍 Escribe *Ubicacion* para horarios y dirección\\n👤 Escribe *Asesor* para hablar con el encargado\\n\\n_🔍 O escribe directamente: \\"agregar takis\\", \\"coca\\", \\"foto aceite\\"..._"'

if old_bienvenida in code:
    code = code.replace(old_bienvenida, new_bienvenida)
    print("Replaced mensajeBienvenida")

# Insert Commerce handlers right before Catálogo completo
target = '// 1. Catálogo completo'
new_handlers = """        // --- 🛒 COMANDOS DE CARRITO Y COMPRAS ---
        if (text == "corte de caja" || text == "corte" || text == "corte caja" || text == "ventas de hoy" || text == "caja" || text == "ventas") {
            return NeoCommerceManager.formatCorteDeCajaReport(context, negocio) to null
        }

        if (text == "carrito" || text == "ver carrito" || text == "mi carrito" || text == "total" || text == "cuenta" || text == "mi pedido" || text == "compras") {
            return NeoCommerceManager.formatCartReceipt(context, "default_cart", negocio) to null
        }

        if (text == "vaciar carrito" || text == "borrar carrito" || text == "limpiar carrito" || text == "cancelar pedido") {
            NeoCommerceManager.clearCart(context, "default_cart")
            return "🗑️ Tu carrito de compras ha sido vaciado correctamente.\\nEscribe *Catalogo* para ver los productos." to null
        }

        if (text == "comprar" || text == "confirmar" || text == "confirmar compra" || text == "confirmar pedido" || text == "pagar" || text == "pedir ya" || text == "finalizar") {
            val (ok, res) = NeoCommerceManager.checkoutCart(context, "default_cart", "Cliente", "Cliente Chat", negocio)
            return res to null
        }

        if (text.startsWith("agregar ") || text.startsWith("agrega ") || text.startsWith("pedir ") || text.startsWith("comprar ")) {
            val q = text.replace("agregar ", "").replace("agrega ", "").replace("pedir ", "").replace("comprar ", "").trim()
            val matched = catalog.firstOrNull { cleanText(it.nombre).contains(q) } ?: catalog.firstOrNull { q.contains(cleanText(it.nombre)) }
            if (matched != null) {
                val item = NeoCommerceManager.addToCart(context, "default_cart", matched, 1)
                val currentTotal = NeoCommerceManager.getCartTotal(context, "default_cart")
                val sb = StringBuilder("🛒 *¡Agregado al Carrito!*\\n\\n")
                sb.append("✅ *${matched.nombre}*\\n")
                sb.append("💵 Cantidad: ${item.cantidad} | Precio: $${matched.precio} MXN\\n")
                sb.append("───────────────────────\\n")
                sb.append("💰 *Total actual en carrito:* $${"%.2f".format(currentTotal)} MXN\\n\\n")
                sb.append("👉 Escribe *carrito* para ver tu cuenta completa.\\n")
                sb.append("👉 Escribe *comprar* para confirmar tu orden.")
                return sb.toString() to (if (matched.imagenBase64.isNotEmpty()) matched.imagenBase64 else null)
            } else {
                return "🔍 No encontré el producto \\"$q\\" en el catálogo.\\nEscribe *Catalogo* para ver la lista completa de artículos disponibles." to null
            }
        }

        // 1. Catálogo completo"""

if target in code:
    code = code.replace(target, new_handlers)
    print("Added commerce handlers before Catálogo")

# Update Catálogo instructions at the bottom of catalog response
old_cat_opts = """            sb.append("\\n\\n💡 *Opciones:*\\n")
            sb.append("- Escribe lo que buscas (ej: *coca*, *aceite*) para ver precio.\\n")
            sb.append("- Para ver foto de un producto escribe: *foto [producto]* (ej: *foto coca*).")"""

new_cat_opts = """            sb.append("\\n\\n🛒 *¿Cómo comprar y ver total?*\\n")
            sb.append("• Escribe: *agregar [producto]* (ej: *agregar takis fuego*)\\n")
            sb.append("• Escribe: *carrito* para ver los artículos y el total\\n")
            sb.append("• Escribe: *comprar* para confirmar tu pedido y caja\\n")
            sb.append("• Escribe: *corte de caja* para ver el resumen de ventas del día\\n")
            sb.append("• Para ver fotos escribe: *foto [producto]*")"""

if old_cat_opts in code:
    code = code.replace(old_cat_opts, new_cat_opts)
    print("Updated Catálogo instructions")

with open('app/src/main/java/com/josuedev/neomessage/NeoBotManager.kt', 'w', encoding='utf-8') as f:
    f.write(code)
print("NeoBotManager.kt updated successfully!")
