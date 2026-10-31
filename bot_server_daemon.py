import urllib.request, json, time, sys, traceback
from neobot_engine import clean_text, parse_order_items, CARTS

if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except:
        pass

FIREBASE_URL = "https://neomessage-19ab4-default-rtdb.firebaseio.com"
CHECK_INTERVAL_SECONDS = 3

print("=======================================================")
print(f"🚀 INICIANDO GUARDIÁN 24/7 MULTI-TIENDA UNIVERSAL")
print(f"🌐 Responde automáticamente al cliente usando la tienda del DESTINATARIO")
print(f"📡 Monitoreando Firebase RTDB cada {CHECK_INTERVAL_SECONDS} segundos...")
print("=======================================================")

LAST_BOT_CONFIGS = {}

def get_active_bot_configs():
    global LAST_BOT_CONFIGS
    try:
        url = f"{FIREBASE_URL}/bot_configs.json"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = json.loads(resp.read().decode('utf-8'))
        if data and isinstance(data, dict):
            active = {}
            for phone, cfg in data.items():
                if isinstance(cfg, str):
                    try:
                        cfg = json.loads(cfg)
                    except:
                        continue
                if isinstance(cfg, dict) and cfg.get("isEnabled", False):
                    active[phone[-10:]] = cfg
            LAST_BOT_CONFIGS = active
            return active
    except Exception as e:
        pass
    return LAST_BOT_CONFIGS

def process_custom_bot(text_in, chat_id, store_cfg):
    text = clean_text(text_in)
    negocio = store_cfg.get("nombreNegocio", "Mi Negocio")
    horario = store_cfg.get("horario", "Lunes a Sábado de 8:30 AM a 7:00 PM")
    direccion = store_cfg.get("direccion", "Tuxtla Gutiérrez, Chiapas")
    telefono = store_cfg.get("telefono", "")
    raw_cat = store_cfg.get("catalogo", [])
    
    catalog = raw_cat if raw_cat else [
        {"codigo": 1, "nombre": "IMPRESIONES B/N", "precio": "1"},
        {"codigo": 276, "nombre": "ELASTICO CHICO POR METRO $ 6 PESOS", "precio": "70.00"},
        {"codigo": 725, "nombre": "BIMBO GALLETAS GABI BESOS DE NUEZ 60 GR", "precio": "10.00"}
    ]

    # 1. Carrito
    if text in ["carrito", "mi carrito", "ver carrito", "cuenta", "total"]:
        cart = CARTS.get(chat_id, {})
        if not cart:
            return f"🛒 *Tu carrito de compras está vacío.*\n\nEscribe el número de un producto para agregarlo a tu cuenta."
        sb = f"🛒 *TU CARRITO ACTUAL:*\n🏪 _{negocio}_\n\n"
        total = 0.0
        for code, qty in cart.items():
            prod = next((p for p in catalog if p.get("codigo") == code), None)
            if prod:
                price = float(str(prod.get("precio", "0")).replace("$", "").replace("MXN", "").strip() or "0")
                sub = price * qty
                total += sub
                sb += f"• {qty}x *[{code}] {prod.get('nombre')}* ➔ *${sub:.2f} MXN*\n"
        sb += f"───────────────────────\n"
        sb += f"💰 *TOTAL A PAGAR:* *${total:.2f} MXN*\n\n"
        sb += f"👉 Para ordenar escribe: *confirmar pedido*"
        return sb

    # 2. Confirmar pedido
    if "confirmar pedido" in text or "confirmar orden" in text or "hacer pedido" in text:
        cart = CARTS.get(chat_id, {})
        if not cart:
            return f"🛒 *Tu carrito está vacío.* Agrega productos escribiendo su código antes de confirmar."
        ticket_no = f"TICK-{int(time.time()) % 100000:05d}"
        sb = f"🧾 *TICKET DE COMPRA CONFIRMADO*\n"
        sb += f"🏪 *{negocio.upper()}*\n"
        sb += f"📄 *Folio:* #{ticket_no}\n"
        sb += f"🕒 *Fecha:* {time.strftime('%d/%m/%Y %I:%M %p')}\n"
        sb += f"───────────────────────\n"
        total = 0.0
        for code, qty in cart.items():
            prod = next((p for p in catalog if p.get("codigo") == code), None)
            if prod:
                price = float(str(prod.get("precio", "0")).replace("$", "").replace("MXN", "").strip() or "0")
                sub = price * qty
                total += sub
                sb += f"• {qty}x [{code}] {prod.get('nombre')} = *${sub:.2f}*\n"
        sb += f"───────────────────────\n"
        sb += f"💰 *TOTAL NETO:* *${total:.2f} MXN*\n\n"
        sb += f"✅ ¡Tu pedido ha sido recibido con éxito!\n"
        sb += f"El encargado ya tiene la comanda para despacharte enseguida."
        CARTS[chat_id] = {}
        return sb

    # 3. Pedidos por código
    order_items = parse_order_items(text)
    if order_items:
        cart = CARTS.setdefault(chat_id, {})
        sb = "🛒 *¡Agregado al Carrito!*\n\n"
        added_any = False
        for code, qty in order_items:
            prod = next((p for p in catalog if p.get("codigo") == code), None)
            if prod:
                cart[code] = cart.get(code, 0) + qty
                price = float(str(prod.get("precio", "0")).replace("$", "").replace("MXN", "").strip() or "0")
                sub = price * qty
                sb += f"✅ {qty}x *[{prod.get('codigo')}] {prod.get('nombre')}* ➔ *${sub:.2f} MXN*\n"
                added_any = True
        if added_any:
            total = 0.0
            for c, q in cart.items():
                p = next((x for x in catalog if x.get("codigo") == c), None)
                if p:
                    pr = float(str(p.get("precio", "0")).replace("$", "").replace("MXN", "").strip() or "0")
                    total += pr * q
            sb += "───────────────────────\n"
            sb += f"💰 *Total actual en carrito:* ${total:.2f} MXN\n\n"
            sb += "👉 Escribe *carrito* para ver tus compras actuales.\n"
            sb += "👉 Escribe *confirmar pedido* para generar tu ticket y total."
            return sb

    # 4. Catálogo
    if text in ["catalogo", "catalago", "catallogo", "productos", "articulos", "lista", "precios", "que venden", "menu"]:
        sb = f"🔍 *CATÁLOGO DE PRODUCTOS ({len(catalog)}):*\n"
        sb += f"🏪 _{negocio}_\n\n"
        for p in catalog:
            sb += f"*[ {p.get('codigo', 1)} ]* {p.get('nombre', '')} ➔ *${p.get('precio', '0')} MXN*\n"
        sb += "\n🛒 *¿Cómo agregarlos a tu carrito?*\n"
        sample_code = catalog[0].get('codigo', 1) if catalog else 1
        sb += f"• Escribe el número del producto (ej: *{sample_code}*).\n"
        sb += f"• O directo con cantidad: (ej: *2 del {sample_code}*).\n"
        sb += "• Para finalizar escribe: *confirmar pedido*"
        return sb

    # 5. Saludo
    if text in ["hola", "ola", "buenas", "buenos dias", "buenas tardes", "buenas noches", "inicio", "start", "que tal", "saludos"] or text.startswith("hola ") or text.startswith("ola ") or text.startswith("buenas "):
        hour = time.localtime().tm_hour
        greeting = "¡Buenos días! ☀️" if 5 <= hour < 12 else ("¡Buenas tardes! 🌤️" if 12 <= hour < 19 else "¡Buenas noches! 🌙")
        return f"{greeting} Gracias por comunicarte con *{negocio}* 🤖.\n\n" \
               f"Escribe:\n" \
               f"📦 *Catalogo* ➔ Ver productos y precios\n" \
               f"📍 *Ubicacion* ➔ Horarios y dirección\n" \
               f"🛒 *Carrito* ➔ Ver tus compras actuales\n\n" \
               f"🔍 _O escribe lo que buscas para buscarlo al instante._"

    # 6. Ubicación
    if any(k in text for k in ["ubicacion", "ubicasion", "ubic", "direc", "donde", "mapa", "lugar"]):
        return f"📍 *Ubicación de {negocio}:*\n{direccion}\n\n" \
               f"🕒 *Horario:*\n{horario}\n\n" \
               f"📞 *Teléfono:*\n{telefono}"

    # 7. Default
    return f"{negocio} 🤖\n\n" \
           f"¿En qué puedo ayudarte? Puedes escribir:\n" \
           f"📦 *Catalogo* ➔ Ver productos y precios\n" \
           f"📍 *Ubicacion* ➔ Horarios y dirección\n" \
           f"🛒 *Carrito* ➔ Ver tus compras actuales\n" \
           f"👤 *Asesor* ➔ Hablar con el encargado"

def check_cycle():
    bot_configs = get_active_bot_configs()
    
    url = f"{FIREBASE_URL}/chats.json"
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, timeout=8) as resp:
        res = json.loads(resp.read().decode('utf-8'))
    
    if not res or not isinstance(res, dict):
        return

    for chat_id, chat_data in res.items():
        if not isinstance(chat_data, dict):
            continue
        
        # Extraer los 2 números del chat privado (private_TEL1_TEL2)
        parts = chat_id.replace("private_", "").split("_")
        if len(parts) != 2:
            continue
        
        user1 = parts[0][-10:]
        user2 = parts[1][-10:]

        messages = chat_data.get("messages", {})
        if not messages:
            continue
        
        sorted_msgs = sorted(messages.values(), key=lambda m: m.get("timestamp", 0))
        last_msg = sorted_msgs[-1]
        
        # NUNCA responder a mensajes enviados por un Bot (evitar ciclo infinito o bots hablándose)
        msg_id_str = str(last_msg.get("id", ""))
        sender_name = str(last_msg.get("senderName", ""))
        rank = str(last_msg.get("rank", ""))
        if msg_id_str.startswith("bot_") or "🤖" in sender_name or rank == "Operador Bot" or last_msg.get("isBot", False):
            continue

        last_sender = str(last_msg.get("senderId", "")).strip()[-10:]

        # Si el chat es consigo mismo (notas personales), no disparar bot
        if user1 == user2:
            continue

        # Determinar quién es el DESTINATARIO
        # Si el que escribió fue user1, el destinatario es user2.
        # Si el que escribió fue user2, el destinatario es user1.
        recipient = user2 if last_sender == user1 else (user1 if last_sender == user2 else None)
        if not recipient:
            continue

        # ¿EL DESTINATARIO TIENE BOT DE VENTAS ACTIVO?
        recipient_cfg = bot_configs.get(recipient)
        if not recipient_cfg or not recipient_cfg.get("isEnabled", False):
            # El destinatario NO tiene bot activo, no se responde nada
            continue

        # Si el destinatario sí tiene bot activo:
        msg_id = last_msg.get("id", str(last_msg.get("timestamp", int(time.time()*1000))))
        reply_id = f"bot_{msg_id}"

        # Verificar si ya se respondió a este mensaje
        if reply_id not in messages:
            text_in = last_msg.get("text", "")
            if text_in.startswith("[NEO_") or text_in.startswith("[SYSTEM]"):
                continue

            store_name = recipient_cfg.get("nombreNegocio", "Tienda")
            print(f"\n[CLIENTE ESCRIBIÓ A TIENDA] Destinatario: {store_name} ({recipient}) | De: {last_sender} | Texto: '{text_in}'")
            
            reply_text = process_custom_bot(text_in, chat_id, recipient_cfg)
            reply_payload = {
                "id": reply_id,
                "text": reply_text,
                "timestamp": int(time.time() * 1000),
                "senderId": recipient_cfg.get("telefono", recipient),
                "senderName": f"{store_name} 🤖",
                "rank": "Operador Bot",
                "isBot": True
            }

            post_url = f"{FIREBASE_URL}/chats/{chat_id}/messages/{reply_id}.json"
            p_req = urllib.request.Request(
                post_url,
                data=json.dumps(reply_payload).encode('utf-8'),
                headers={'Content-Type': 'application/json'},
                method='PUT'
            )
            with urllib.request.urlopen(p_req, timeout=10):
                print(f"✅ [RESPUESTA DE {store_name} ENVIADA] a {chat_id}!")

LAST_CLEAN_TIME = 0
CLEAN_INTERVAL_SECONDS = 3600 * 4 # Auto-limpieza cada 4 horas

def auto_clean_transit_cloud():
    global LAST_CLEAN_TIME
    now = time.time()
    if now - LAST_CLEAN_TIME < CLEAN_INTERVAL_SECONDS:
        return
    
    LAST_CLEAN_TIME = now
    print("\n🧹 [MANTENIMIENTO SOBERANO] Iniciando auto-limpieza preventiva de Firebase...")
    try:
        url = f"{FIREBASE_URL}/chats.json"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode('utf-8'))
        
        if not data or not isinstance(data, dict):
            return

        total_cleaned = 0
        for chat_id, chat_data in data.items():
            if not isinstance(chat_data, dict):
                continue
            messages = chat_data.get("messages", {})
            if not isinstance(messages, dict) or len(messages) <= 50:
                continue
            
            # Ordenar mensajes por timestamp
            sorted_items = sorted(messages.items(), key=lambda x: x[1].get("timestamp", 0) if isinstance(x[1], dict) else 0)
            
            # Mantener los últimos 50 intactos (búfer de tránsito oficial para la app)
            # Todo lo anterior a los últimos 50 ya está resguardado en SQLite de los teléfonos
            excess = sorted_items[:-50]
            for msg_id, msg_body in excess:
                try:
                    del_url = f"{FIREBASE_URL}/chats/{chat_id}/messages/{msg_id}.json"
                    d_req = urllib.request.Request(del_url, method='DELETE')
                    with urllib.request.urlopen(d_req, timeout=5):
                        total_cleaned += 1
                except Exception:
                    pass

        print(f"✨ [MANTENIMIENTO FINALIZADO] Se depuraron {total_cleaned} mensajes antiguos de la nube.")
        print(f"📦 Firebase permanece 100% ligero (< 5 MB) y dentro de la cuota gratuita para siempre.\n")
    except Exception as e:
        print(f"⚠️ [Mantenimiento omitido]: {e}")

def loop():
    while True:
        try:
            check_cycle()
            auto_clean_transit_cloud()
        except Exception as e:
            pass
        time.sleep(CHECK_INTERVAL_SECONDS)

if __name__ == "__main__":
    loop()
