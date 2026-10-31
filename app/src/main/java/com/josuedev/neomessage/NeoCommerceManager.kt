package com.josuedev.neomessage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class CartItem(
    val nombre: String,
    val precio: Double,
    var cantidad: Int = 1
) {
    val subtotal: Double get() = precio * cantidad
}

data class SaleRecord(
    val id: String = UUID.randomUUID().toString().take(8).uppercase(),
    val timestamp: Long = System.currentTimeMillis(),
    val customerPhone: String = "",
    val customerName: String = "Cliente",
    val items: List<CartItem> = emptyList(),
    val total: Double = 0.0,
    val metodoPago: String = "Efectivo",
    val status: String = "PENDIENTE" // "PENDIENTE" (por recoger / no fue a traer), "ENTREGADO" (recogido y cobrado)
)

object NeoCommerceManager {
    private const val PREFS_COMMERCE = "neo_commerce_prefs"
    private const val KEY_CART_PREFIX = "cart_"
    private const val KEY_SALES_HISTORY = "sales_history_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_COMMERCE, Context.MODE_PRIVATE)
    }

    // ==================== 🛒 GESTIÓN DEL CARRITO DE COMPRAS ====================

    fun getCart(context: Context, chatId: String): MutableList<CartItem> {
        val sp = getPrefs(context)
        val json = sp.getString(KEY_CART_PREFIX + chatId, null) ?: return mutableListOf()
        val list = mutableListOf<CartItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CartItem(
                        nombre = obj.getString("nombre"),
                        precio = obj.getDouble("precio"),
                        cantidad = obj.optInt("cantidad", 1)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveCart(context: Context, chatId: String, cart: List<CartItem>) {
        val sp = getPrefs(context)
        val arr = JSONArray()
        cart.forEach {
            val obj = JSONObject()
            obj.put("nombre", it.nombre)
            obj.put("precio", it.precio)
            obj.put("cantidad", it.cantidad)
            arr.put(obj)
        }
        sp.edit().putString(KEY_CART_PREFIX + chatId, arr.toString()).apply()
    }

    fun addToCart(context: Context, chatId: String, product: BotProduct, qty: Int = 1): CartItem {
        val cart = getCart(context, chatId)
        val cleanPrice = product.precio.replace("[^0-9.]".toRegex(), "").toDoubleOrNull() ?: 0.0
        val existing = cart.firstOrNull { it.nombre.equals(product.nombre, ignoreCase = true) }
        val item: CartItem
        if (existing != null) {
            existing.cantidad += qty
            item = existing
        } else {
            item = CartItem(product.nombre, cleanPrice, qty)
            cart.add(item)
        }
        saveCart(context, chatId, cart)
        return item
    }

    fun updateQuantity(context: Context, chatId: String, productName: String, newQty: Int) {
        val cart = getCart(context, chatId)
        if (newQty <= 0) {
            cart.removeAll { it.nombre.equals(productName, ignoreCase = true) }
        } else {
            cart.firstOrNull { it.nombre.equals(productName, ignoreCase = true) }?.cantidad = newQty
        }
        saveCart(context, chatId, cart)
    }

    fun removeFromCart(context: Context, chatId: String, productName: String) {
        val cart = getCart(context, chatId)
        cart.removeAll { it.nombre.equals(productName, ignoreCase = true) }
        saveCart(context, chatId, cart)
    }

    fun clearCart(context: Context, chatId: String) {
        getPrefs(context).edit().remove(KEY_CART_PREFIX + chatId).apply()
    }

    fun getCartTotal(context: Context, chatId: String): Double {
        return getCart(context, chatId).sumOf { it.subtotal }
    }

    fun formatCartReceipt(context: Context, chatId: String, negocio: String): String {
        val cart = getCart(context, chatId)
        if (cart.isEmpty()) {
            return "🛒 Tu carrito de compras está vacío.\nEscribe *Catalogo* para ver nuestros productos y agregar lo que necesites."
        }
        val sb = StringBuilder("🛒 *Tu Carrito de Compras en $negocio:*\n\n")
        var idx = 1
        cart.forEach {
            sb.append("$idx. *${it.nombre}*\n   ${it.cantidad} x $${"%.2f".format(it.precio)} = *$${"%.2f".format(it.subtotal)} MXN*\n")
            idx++
        }
        val total = cart.sumOf { it.subtotal }
        sb.append("\n───────────────────────\n")
        sb.append("💵 *TOTAL A PAGAR: $${"%.2f".format(total)} MXN*\n")
        sb.append("───────────────────────\n\n")
        sb.append("👉 Escribe *confirmar pedido* para cerrar tu orden.\n")
        sb.append("👉 Escribe *vaciar carrito* para empezar de nuevo.\n")
        sb.append("👉 Escribe *catalogo* para añadir más productos.")
        return sb.toString()
    }

    // ==================== 💰 CORTE DE CAJA Y REGISTRO DE VENTAS ====================

    fun recordSale(context: Context, sale: SaleRecord) {
        val list = getAllSales(context).toMutableList()
        list.add(0, sale) // La más reciente al principio
        if (list.size > 200) list.removeAt(list.size - 1)
        saveSalesList(context, list)

        // También sincronizar a Firebase si hay red
        try {
            val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(sale.timestamp))
            db.getReference("caja_ventas").child(dateKey).child(sale.id).setValue(sale)
        } catch (_: Exception) {}
    }

    fun updateSaleStatus(context: Context, saleId: String, newStatus: String) {
        val list = getAllSales(context).toMutableList()
        val index = list.indexOfFirst { it.id == saleId }
        if (index != -1) {
            val updated = list[index].copy(status = newStatus)
            list[index] = updated
            saveSalesList(context, list)
            try {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(updated.timestamp))
                db.getReference("caja_ventas").child(dateKey).child(updated.id).child("status").setValue(newStatus)
            } catch (_: Exception) {}
        }
    }

    fun deleteSale(context: Context, saleId: String) {
        val list = getAllSales(context).toMutableList()
        val removed = list.firstOrNull { it.id == saleId }
        list.removeAll { it.id == saleId }
        saveSalesList(context, list)
        if (removed != null) {
            try {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance(NetworkConfig.FIREBASE_DATABASE_URL)
                val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(removed.timestamp))
                db.getReference("caja_ventas").child(dateKey).child(removed.id).removeValue()
            } catch (_: Exception) {}
        }
    }

    private fun saveSalesList(context: Context, list: List<SaleRecord>) {
        val sp = getPrefs(context)
        val arr = JSONArray()
        list.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("timestamp", s.timestamp)
            obj.put("customerPhone", s.customerPhone)
            obj.put("customerName", s.customerName)
            obj.put("total", s.total)
            obj.put("metodoPago", s.metodoPago)
            obj.put("status", s.status)
            val itemsArr = JSONArray()
            s.items.forEach { itm ->
                val io = JSONObject()
                io.put("nombre", itm.nombre)
                io.put("precio", itm.precio)
                io.put("cantidad", itm.cantidad)
                itemsArr.put(io)
            }
            obj.put("items", itemsArr)
            arr.put(obj)
        }
        sp.edit().putString(KEY_SALES_HISTORY, arr.toString()).apply()
    }

    fun getAllSales(context: Context): List<SaleRecord> {
        val sp = getPrefs(context)
        val json = sp.getString(KEY_SALES_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<SaleRecord>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val itemsArr = obj.optJSONArray("items") ?: JSONArray()
                val itemsList = mutableListOf<CartItem>()
                for (j in 0 until itemsArr.length()) {
                    val io = itemsArr.getJSONObject(j)
                    itemsList.add(CartItem(io.getString("nombre"), io.getDouble("precio"), io.getInt("cantidad")))
                }
                list.add(
                    SaleRecord(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        customerPhone = obj.optString("customerPhone", ""),
                        customerName = obj.optString("customerName", "Cliente"),
                        items = itemsList,
                        total = obj.getDouble("total"),
                        metodoPago = obj.optString("metodoPago", "Efectivo"),
                        status = obj.optString("status", "PENDIENTE")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun getTodaySales(context: Context): List<SaleRecord> {
        val all = getAllSales(context)
        val calNow = Calendar.getInstance()
        return all.filter {
            val calSale = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            calNow.get(Calendar.YEAR) == calSale.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calSale.get(Calendar.DAY_OF_YEAR)
        }
    }

    fun checkoutCart(context: Context, chatId: String, customerPhone: String, customerName: String, negocio: String): Pair<Boolean, String> {
        val cart = getCart(context, chatId)
        if (cart.isEmpty()) {
            return false to "El carrito está vacío. Agrega productos con *catalogo* o *agregar [producto]*."
        }
        val total = cart.sumOf { it.subtotal }
        val sale = SaleRecord(
            customerPhone = customerPhone,
            customerName = customerName.ifEmpty { "Cliente" },
            items = cart.toList(),
            total = total,
            metodoPago = "Efectivo",
            status = "PENDIENTE" // Inicia como pendiente hasta que vayan a recogerlo
        )
        recordSale(context, sale)
        clearCart(context, chatId)

        val sb = StringBuilder("🎉 *¡PEDIDO CONFIRMADO Y REGISTRADO!*\n\n")
        sb.append("🧾 *Ticket de Venta #NEO-${sale.id}*\n")
        sb.append("🏪 $negocio\n")
        sb.append("👤 Cliente: ${sale.customerName}\n")
        sb.append("🕒 Hora: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sale.timestamp))}\n")
        sb.append("───────────────────────\n")
        sale.items.forEach {
            sb.append("• ${it.nombre} (${it.cantidad}x $${"%.2f".format(it.precio)}) = $${"%.2f".format(it.subtotal)}\n")
        }
        sb.append("───────────────────────\n")
        sb.append("💰 *TOTAL A PAGAR: $${"%.2f".format(total)} MXN*\n")
        sb.append("💵 Método: Efectivo al recoger\n\n")
        sb.append("⏳ *Estado:* Pendiente de recolección en mostrador.\n")
        sb.append("¡Gracias por su compra en *$negocio*! 🌟")
        return true to sb.toString()
    }

    fun formatCorteDeCajaReport(context: Context, negocio: String): String {
        val todaySales = getTodaySales(context)
        val deliveredSales = todaySales.filter { it.status == "ENTREGADO" }
        val pendingSales = todaySales.filter { it.status != "ENTREGADO" }

        val totalDelivered = deliveredSales.sumOf { it.total }
        val totalPending = pendingSales.sumOf { it.total }
        val count = deliveredSales.size
        val avgTicket = if (count > 0) totalDelivered / count else 0.0
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val sb = StringBuilder("📊 *CORTE DE CAJA DEL DÍA*\n")
        sb.append("🏪 *$negocio*\n")
        sb.append("📅 Fecha de Cierre: $dateStr\n")
        sb.append("═══════════════════════\n")
        sb.append("💰 *TOTAL COBRADO (Entregados): $${"%.2f".format(totalDelivered)} MXN*\n")
        sb.append("⏳ *Pendientes de Recoger: $${"%.2f".format(totalPending)} MXN (${pendingSales.size} pedidos)*\n")
        sb.append("🧾 *Ventas Entregadas:* $count transacciones\n")
        sb.append("📈 *Ticket Promedio:* $${"%.2f".format(avgTicket)} MXN\n")
        sb.append("═══════════════════════\n\n")

        if (todaySales.isEmpty()) {
            sb.append("_No se han registrado ventas el día de hoy._\n")
            sb.append("Las ventas se registran automáticamente cuando los clientes confirman su compra en el carrito.")
        } else {
            sb.append("📋 *Resumen de Órdenes:*\n")
            todaySales.take(10).forEach { s ->
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.timestamp))
                val icon = if (s.status == "ENTREGADO") "✅" else "⏳"
                val prods = s.items.joinToString(", ") { "${it.cantidad}x ${it.nombre.take(12)}" }
                sb.append("$icon [$time] *#${s.id}* (${s.status}): $${"%.2f".format(s.total)} MXN\n  _${prods}_\n")
            }
            if (todaySales.size > 10) {
                sb.append("_...y ${todaySales.size - 10} ventas más hoy._\n")
            }
        }
        return sb.toString()
    }
}
