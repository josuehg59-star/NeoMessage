import re, unicodedata, json, time, uuid, urllib.request

DEFAULT_CATALOG = [
    {"codigo": 1, "nombre": "IMPRESIONES B/N", "precio": "1"},
    {"codigo": 2, "nombre": "IMPRESIONES A COLOR LETRAS", "precio": "2"},
    {"codigo": 3, "nombre": "IMPRESIONES A COLOR IMÁGENES GDES", "precio": "5"},
    {"codigo": 4, "nombre": "CARGA DE BATERIA COCHE O MOTO", "precio": "50"},
    {"codigo": 5, "nombre": "ACEITE NUTRIOLI COMESTIBLE PURO DE SOYA 400 ML", "precio": "24"},
    {"codigo": 6, "nombre": "ACEITE NUTRIOLI COMESTIBLE PURO DE SOYA 850 ML", "precio": "44"},
    {"codigo": 7, "nombre": "AGUA CIEL PURIFICADA BOTTLE 1 LTS NR", "precio": "16"},
    {"codigo": 8, "nombre": "AGUA CRISTAL PURIFICADA 20 LTS", "precio": "50"},
    {"codigo": 9, "nombre": "AZUCAR KILO", "precio": "22"},
    {"codigo": 10, "nombre": "COCA COLA 600 ML", "precio": "18"},
    {"codigo": 11, "nombre": "COCA COLA 2.5 LTS", "precio": "38"},
    {"codigo": 12, "nombre": "HUEVO CONO 30 PZAS", "precio": "85"},
    {"codigo": 13, "nombre": "LECHE LALA ENTERA 1 LTS", "precio": "26"},
    {"codigo": 26, "nombre": "ALIMENTO PEDIGREE RAZA PEQUEÑA RES Y VEGETALES 420 GR", "precio": "30.00"},
    {"codigo": 45, "nombre": "BARCEL BIG MIX QUESO 185 GR", "precio": "40.00"},
    {"codigo": 46, "nombre": "BARCEL BIG MIX QUESO 75 GR", "precio": "22.00"},
    {"codigo": 67, "nombre": "BARCEL POP QUESO CHEDDAR 45 GR", "precio": "17.00"},
    {"codigo": 79, "nombre": "BARCEL TAKIS PIX QUESO 65 GR", "precio": "16.00"},
    {"codigo": 86, "nombre": "BARCEL WAPAS QUESO 52 GR", "precio": "17.00"},
    {"codigo": 276, "nombre": "ELASTICO CHICO POR METRO $ 6 PESOS", "precio": "70.00"},
    {"codigo": 725, "nombre": "BIMBO GALLETAS GABI BESOS DE NUEZ 60 GR", "precio": "10.00"}
]

# In-memory cart per chat: { chatId: { codigo: qty } }
CARTS = {}

def clean_text(raw):
    nfd = unicodedata.normalize('NFD', raw.lower().strip())
    without_accents = ''.join(c for c in nfd if unicodedata.category(c) != 'Mn')
    cleaned = re.sub(r'[^a-z0-9 ]', ' ', without_accents)
    return re.sub(r'\s+', ' ', cleaned).strip()

def parse_order_items(raw_text):
    text = raw_text.lower().strip()
    results = []

    # 1 del 276 y 2 del 725
    r1 = re.findall(r'(\d+)\s*(?:del?|de)\s*(\d+)', text)
    if r1:
        for qty_s, code_s in r1:
            try:
                results.append((int(code_s), int(qty_s)))
            except:
                pass
        return results

    # del 1 requiero 2
    r2 = re.findall(r'(?:del?|de)\s*(\d+)\s*(?:requier[op]|quier[op]|pid[op]|necesit[op]|dame|ponme|agreg[ao])\s*(\d+)', text)
    if r2:
        for code_s, qty_s in r2:
            try:
                results.append((int(code_s), int(qty_s)))
            except:
                pass
        return results

    # del 276 (cantidad 1 por defecto)
    r3 = re.match(r'^(?:del?|de)\s*(\d+)$', text)
    if r3:
        try:
            return [(int(r3.group(1)), 1)]
        except:
            pass

    # 276 2 (código y cantidad)
    r4 = re.match(r'^(?:agregar\s+|pedir\s+|comprar\s+)?(\d+)\s+(\d+)$', text)
    if r4:
        try:
            return [(int(r4.group(1)), int(r4.group(2)))]
        except:
            pass

    # solo código numérico: 276
    r5 = re.match(r'^(?:agregar\s+|pedir\s+|comprar\s+)?(\d+)$', text)
    if r5:
        try:
            code = int(r5.group(1))
            if code > 0:
                return [(code, 1)]
        except:
            pass

    return results

def process_bot_message(incoming_text, chat_id="default"):
    text = clean_text(incoming_text)
    negocio = "miscelanea brittany"
    catalog = DEFAULT_CATALOG

    # 1. Carrito
    if text in ["carrito", "mi carrito", "ver carrito", "cuenta", "total"]:
        cart = CARTS.get(chat_id, {})
        if not cart:
            return f"🛒 *Tu carrito de compras está vacío.*\n\nEscribe el número de un producto (ej: *276* o *2 del 276*) para agregarlo a tu cuenta."
        sb = f"🛒 *TU CARRITO ACTUAL:*\n🏪 _{negocio}_\n\n"
        total = 0.0
        for code, qty in cart.items():
            prod = next((p for p in catalog if p["codigo"] == code), None)
            if prod:
                price = float(re.sub(r'[^0-9.]', '', prod["precio"]) or "0")
                sub = price * qty
                total += sub
                sb += f"• {qty}x *[{code}] {prod['nombre']}* ➔ *${sub:.2f} MXN*\n"
        sb += f"───────────────────────\n"
        sb += f"💰 *TOTAL A PAGAR:* *${total:.2f} MXN*\n\n"
        sb += f"👉 Para ordenar escribe: *confirmar pedido*\n"
        sb += f"👉 Para vaciar escribe: *vaciar carrito*"
        return sb

    # 2. Vaciar carrito
    if "vaciar carrito" in text or "borrar carrito" in text or "cancelar pedido" in text:
        CARTS[chat_id] = {}
        return f"🗑️ *Carrito vaciado correctamente.*\n\nPuedes consultar el *catálogo* cuando gustes."

    # 3. Confirmar pedido
    if "confirmar pedido" in text or "confirmar orden" in text or "hacer pedido" in text or "terminar pedido" in text:
        cart = CARTS.get(chat_id, {})
        if not cart:
            return f"🛒 *Tu carrito está vacío.* Agrega productos escribiendo su código (ej: *276* o *2 del 276*) antes de confirmar."
        ticket_no = f"TICK-{int(time.time()) % 100000:05d}"
        sb = f"🧾 *TICKET DE COMPRA CONFIRMADO*\n"
        sb += f"🏪 *{negocio.upper()}*\n"
        sb += f"📄 *Folio:* #{ticket_no}\n"
        sb += f"🕒 *Fecha:* {time.strftime('%d/%m/%Y %I:%M %p')}\n"
        sb += f"───────────────────────\n"
        total = 0.0
        for code, qty in cart.items():
            prod = next((p for p in catalog if p["codigo"] == code), None)
            if prod:
                price = float(re.sub(r'[^0-9.]', '', prod["precio"]) or "0")
                sub = price * qty
                total += sub
                sb += f"• {qty}x [{code}] {prod['nombre']} = *${sub:.2f}*\n"
        sb += f"───────────────────────\n"
        sb += f"💰 *TOTAL NETO:* *${total:.2f} MXN*\n\n"
        sb += f"✅ ¡Tu pedido ha sido recibido con éxito!\n"
        sb += f"El encargado ya tiene la comanda para despacharte enseguida."
        CARTS[chat_id] = {}
        return sb

    # 4. Pedido inteligente por códigos
    order_items = parse_order_items(text)
    if order_items:
        cart = CARTS.setdefault(chat_id, {})
        sb = "🛒 *¡Agregado al Carrito!*\n\n"
        added_any = False
        for code, qty in order_items:
            prod = next((p for p in catalog if p["codigo"] == code), None)
            if prod:
                cart[code] = cart.get(code, 0) + qty
                price = float(re.sub(r'[^0-9.]', '', prod["precio"]) or "0")
                sub = price * qty
                sb += f"✅ {qty}x *[{prod['codigo']}] {prod['nombre']}* ➔ *${sub:.2f} MXN*\n"
                added_any = True
        if added_any:
            total = sum(float(re.sub(r'[^0-9.]', '', next((p['precio'] for p in catalog if p['codigo'] == c), '0'))) * q for c, q in cart.items())
            sb += "───────────────────────\n"
            sb += f"💰 *Total actual en carrito:* ${total:.2f} MXN\n\n"
            sb += "👉 Escribe *carrito* para ver tus compras actuales.\n"
            sb += "👉 Escribe *confirmar pedido* para generar tu ticket y total."
            return sb

    # 5. Catálogo completo
    if text in ["catalogo", "catalago", "catallogo", "productos", "articulos", "lista", "precios", "que venden", "menu"]:
        sb = f"🔍 *CATÁLOGO DE PRODUCTOS ({len(catalog)}):*\n"
        sb += f"🏪 _{negocio}_\n\n"
        for p in catalog:
            sb += f"*[ {p['codigo']} ]* {p['nombre']} ➔ *${p['precio']} MXN*\n"
        sb += "\n🛒 *¿Cómo agregarlos a tu carrito?*\n"
        sb += "• Escribe el número del producto (ej: *276* o *725*).\n"
        sb += "• O directo con cantidad: (ej: *2 del 276*).\n"
        sb += "• O varios juntos: (ej: *1 del 276 y 2 del 725*).\n"
        sb += "• Para finalizar escribe: *confirmar pedido*"
        return sb

    # 6. Saludo
    if text in ["hola", "ola", "buenas", "buenos dias", "buenas tardes", "buenas noches", "inicio", "start", "que tal", "saludos"] or text.startswith("hola ") or text.startswith("ola ") or text.startswith("buenas "):
        hour = time.localtime().tm_hour
        greeting = "¡Buenos días! ☀️" if 5 <= hour < 12 else ("¡Buenas tardes! 🌤️" if 12 <= hour < 19 else "¡Buenas noches! 🌙")
        return f"{greeting} Gracias por comunicarte con *{negocio}* 🤖.\n\n" \
               f"Escribe:\n" \
               f"📦 *Catalogo* ➔ Ver productos y precios\n" \
               f"📍 *Ubicacion* ➔ Horarios y dirección\n" \
               f"🛒 *Carrito* ➔ Ver tus compras actuales\n\n" \
               f"🔍 _O escribe lo que buscas (ej: \"coca\", \"aceite\", \"sabritas\") para buscarlo al instante._"

    # 7. Ubicación y horarios
    if any(k in text for k in ["ubicacion", "ubicasion", "ubic", "direc", "donde", "mapa", "lugar", "como llegar"]):
        return f"📍 *Ubicación de {negocio}:*\nAv. 16 de septiembre 903, B.S., Tuxtla Gutiérrez, Chiapas\n\n" \
               f"🕒 *Horario:*\nLunes a Sábado de 8:30 AM a 7:00 PM\n\n" \
               f"📞 *Teléfono de contacto:*\n9612765041\n\n" \
               f"🗺️ *Ver en Google Maps:*\nhttps://maps.google.com/?q=Av.+16+de+septiembre+903+Tuxtla+Gutierrez"

    # 8. Asesor
    if any(k in text for k in ["asesor", "human", "encargad", "person", "ayuda"]):
        return f"👤 *Atención Personalizada*\nUn momento por favor, le estoy avisando al encargado de *{negocio}* para que te atienda personalmente."

    # 9. Búsqueda por palabras clave
    ignored = {"tienen", "precio", "precios", "cuanto", "cuesta", "cuestan", "venden", "vende", "hay", "para", "quiero", "dame", "me", "das", "un", "una", "de", "el", "la", "los", "las", "por", "favor", "requiero"}
    tokens = [t for t in text.split(" ") if len(t) >= 2 and t not in ignored]
    matches = [p for p in catalog if any(t in clean_text(p["nombre"]) for t in tokens) or clean_text(p["nombre"]) in text or text in clean_text(p["nombre"])]

    if matches:
        sb = f"🔍 *PRODUCTOS ENCONTRADOS ({len(matches)}):*\n"
        sb += f"🏪 _{negocio}_\n\n"
        for p in matches[:10]:
            sb += f"*[{p['codigo']}]* {p['nombre']} ➔ *${p['precio']} MXN*\n"
        if len(matches) > 10:
            sb += f"\n_Hay {len(matches) - 10} artículos más coincidentes._\n"
        c1 = matches[0]["codigo"]
        c2 = matches[1]["codigo"] if len(matches) > 1 else c1 + 1
        sb += "\n🛒 *¿Cómo agregarlos a tu carrito?*\n"
        sb += f"• Escribe el número del producto (ej: *{c1}* o *{c2}*).\n"
        sb += f"• O directo con cantidad: (ej: *2 del {c1}*).\n"
        sb += f"• O varios juntos: (ej: *1 del {c1} y 2 del {c2}*)."
        return sb

    # 10. Default
    return f"{negocio} 🤖\n\n" \
           f"¿En qué puedo ayudarte? Puedes escribir:\n" \
           f"📦 *Catalogo* ➔ Ver productos y precios\n" \
           f"📍 *Ubicacion* ➔ Horarios y dirección\n" \
           f"🛒 *Carrito* ➔ Ver tus compras actuales\n" \
           f"👤 *Asesor* ➔ Hablar con el encargado\n\n" \
           f"🔍 _O escribe el producto que buscas (ej: \"coca\", \"aceite\", \"sabritas\")._"
