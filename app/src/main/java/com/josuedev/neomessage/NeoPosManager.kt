package com.josuedev.neomessage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PosCustomer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class PosSaleItem(
    val codigo: Int = 0,
    val nombre: String = "",
    val precioUnitario: Double = 0.0,
    val cantidad: Int = 1
) {
    val subtotal: Double get() = precioUnitario * cantidad
}

data class PosSaleRecord(
    val id: String = UUID.randomUUID().toString(),
    val folio: String = "",
    val customerId: String = "mostrador",
    val customerName: String = "Público en General",
    val customerPhone: String = "",
    val items: List<PosSaleItem> = emptyList(),
    val total: Double = 0.0,
    val isPaid: Boolean = true, // true = APLICADO AL CORTE (Vendido/Cobrado), false = PENDIENTE / TICKET CHATBOT SIN CONFIRMAR
    val source: String = "POS", // "POS" o "CHATBOT"
    val timestamp: Long = System.currentTimeMillis()
)

object NeoPosManager {
    private const val PREF_NAME = "neopos_store_prefs"
    private const val KEY_POS_ENABLED = "pos_enabled"
    private const val KEY_CUSTOMERS = "pos_customers_json"
    private const val KEY_SALES = "pos_sales_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isPosEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_POS_ENABLED, true)
    }

    fun setPosEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_POS_ENABLED, enabled).apply()
    }

    fun loadCustomers(context: Context): List<PosCustomer> {
        val raw = prefs(context).getString(KEY_CUSTOMERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PosCustomer>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    PosCustomer(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", ""),
                        phone = o.optString("phone", ""),
                        notes = o.optString("notes", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list.sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomers(context: Context, customers: List<PosCustomer>) {
        try {
            val arr = JSONArray()
            customers.forEach { c ->
                val o = JSONObject()
                o.put("id", c.id)
                o.put("name", c.name)
                o.put("phone", c.phone)
                o.put("notes", c.notes)
                o.put("createdAt", c.createdAt)
                arr.put(o)
            }
            prefs(context).edit().putString(KEY_CUSTOMERS, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun addCustomer(context: Context, name: String, phone: String, notes: String): PosCustomer {
        val current = loadCustomers(context).toMutableList()
        val newC = PosCustomer(
            id = "cli_" + System.currentTimeMillis(),
            name = name.trim(),
            phone = phone.replace(Regex("[^0-9]"), "").takeLast(10),
            notes = notes.trim(),
            createdAt = System.currentTimeMillis()
        )
        current.add(newC)
        saveCustomers(context, current)
        return newC
    }

    fun deleteCustomer(context: Context, customerId: String) {
        val current = loadCustomers(context).filterNot { it.id == customerId }
        saveCustomers(context, current)
    }

    /**
     * Carga todas las ventas y también sincroniza cualquier ticket generado por el ChatBot (NeoCommerceManager)
     * para que los tickets del ChatBot aparezcan como PENDIENTES (sin sumar al corte hasta que se apliquen,
     * o permitiendo eliminarlos si no se vendieron).
     */
    fun loadSales(context: Context): List<PosSaleRecord> {
        val raw = prefs(context).getString(KEY_SALES, null)
        val posList = mutableListOf<PosSaleRecord>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val itemsArr = o.optJSONArray("items") ?: JSONArray()
                    val items = mutableListOf<PosSaleItem>()
                    for (j in 0 until itemsArr.length()) {
                        val io = itemsArr.getJSONObject(j)
                        items.add(
                            PosSaleItem(
                                codigo = io.optInt("codigo", 0),
                                nombre = io.optString("nombre", ""),
                                precioUnitario = io.optDouble("precioUnitario", 0.0),
                                cantidad = io.optInt("cantidad", 1)
                            )
                        )
                    }
                    posList.add(
                        PosSaleRecord(
                            id = o.optString("id", UUID.randomUUID().toString()),
                            folio = o.optString("folio", "#0001"),
                            customerId = o.optString("customerId", "mostrador"),
                            customerName = o.optString("customerName", "Público en General"),
                            customerPhone = o.optString("customerPhone", ""),
                            items = items,
                            total = o.optDouble("total", 0.0),
                            isPaid = o.optBoolean("isPaid", true),
                            source = o.optString("source", "POS"),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // Unificar con los tickets del ChatBot en NeoCommerceManager
        try {
            val botSales = NeoCommerceManager.getAllSales(context)
            var changed = false
            for (bs in botSales) {
                val existingIdx = posList.indexOfFirst { it.id == bs.id || it.folio == "#NEO-${bs.id}" }
                val isApplied = bs.status == "ENTREGADO"
                if (existingIdx == -1) {
                    posList.add(
                        PosSaleRecord(
                            id = bs.id,
                            folio = "#BOT-${bs.id}",
                            customerId = bs.customerPhone.ifBlank { "chatbot" },
                            customerName = bs.customerName.ifBlank { "Cliente ChatBot" },
                            customerPhone = bs.customerPhone,
                            items = bs.items.map { PosSaleItem(0, it.nombre, it.precio, it.cantidad) },
                            total = bs.total,
                            isPaid = isApplied,
                            source = "CHATBOT",
                            timestamp = bs.timestamp
                        )
                    )
                    changed = true
                } else if (posList[existingIdx].isPaid != isApplied) {
                    posList[existingIdx] = posList[existingIdx].copy(isPaid = isApplied)
                    changed = true
                }
            }
            if (changed) {
                saveSales(context, posList)
            }
        } catch (_: Exception) {}

        return posList.sortedByDescending { it.timestamp }
    }

    fun saveSales(context: Context, sales: List<PosSaleRecord>) {
        try {
            val arr = JSONArray()
            sales.take(500).forEach { s ->
                val o = JSONObject()
                o.put("id", s.id)
                o.put("folio", s.folio)
                o.put("customerId", s.customerId)
                o.put("customerName", s.customerName)
                o.put("customerPhone", s.customerPhone)
                o.put("total", s.total)
                o.put("isPaid", s.isPaid)
                o.put("source", s.source)
                o.put("timestamp", s.timestamp)
                val iArr = JSONArray()
                s.items.forEach { item ->
                    val io = JSONObject()
                    io.put("codigo", item.codigo)
                    io.put("nombre", item.nombre)
                    io.put("precioUnitario", item.precioUnitario)
                    io.put("cantidad", item.cantidad)
                    iArr.put(io)
                }
                o.put("items", iArr)
                arr.put(o)
            }
            prefs(context).edit().putString(KEY_SALES, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun recordSale(
        context: Context,
        customer: PosCustomer?,
        items: List<PosSaleItem>,
        isPaid: Boolean
    ): PosSaleRecord {
        val current = loadSales(context).toMutableList()
        val shortId = UUID.randomUUID().toString().take(6).uppercase()
        val folio = "#POS-$shortId"
        val total = items.sumOf { it.subtotal }
        val now = System.currentTimeMillis()
        val record = PosSaleRecord(
            id = shortId,
            folio = folio,
            customerId = customer?.id ?: "mostrador",
            customerName = customer?.name?.ifBlank { "Público en General" } ?: "Público en General",
            customerPhone = customer?.phone ?: "",
            items = items,
            total = total,
            isPaid = isPaid,
            source = "POS",
            timestamp = now
        )
        current.add(0, record)
        saveSales(context, current)

        // Sincronizar también con NeoCommerceManager para que aparezca en Corte de Caja general
        try {
            NeoCommerceManager.recordSale(
                context,
                SaleRecord(
                    id = shortId,
                    timestamp = now,
                    customerPhone = record.customerPhone,
                    customerName = record.customerName,
                    items = items.map { CartItem(it.nombre, it.precioUnitario, it.cantidad) },
                    total = total,
                    metodoPago = "Efectivo",
                    status = if (isPaid) "ENTREGADO" else "PENDIENTE"
                )
            )
        } catch (_: Exception) {}

        return record
    }

    fun toggleSalePaid(context: Context, saleId: String) {
        val current = loadSales(context)
        val target = current.firstOrNull { it.id == saleId } ?: return
        val newPaid = !target.isPaid
        val updated = current.map {
            if (it.id == saleId) it.copy(isPaid = newPaid) else it
        }
        saveSales(context, updated)
        try {
            NeoCommerceManager.updateSaleStatus(context, saleId, if (newPaid) "ENTREGADO" else "PENDIENTE")
        } catch (_: Exception) {}
    }

    fun deleteSale(context: Context, saleId: String) {
        val updated = loadSales(context).filterNot { it.id == saleId }
        saveSales(context, updated)
        try {
            NeoCommerceManager.deleteSale(context, saleId)
        } catch (_: Exception) {}
    }

    fun buildTicketText(storeName: String, sale: PosSaleRecord): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("🧾 *NOTA DE VENTA ${sale.folio}*\n")
        sb.append("🏪 *${storeName.uppercase()}*\n")
        sb.append("📅 Fecha: ${sdf.format(Date(sale.timestamp))}\n")
        sb.append("👤 Cliente: *${sale.customerName}*\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sale.items.forEach { item ->
            sb.append("• ${item.cantidad}x ${item.nombre} ($${String.format(Locale.US, "%.2f", item.precioUnitario)}) = *$${String.format(Locale.US, "%.2f", item.subtotal)}*\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("💰 *TOTAL: $${String.format(Locale.US, "%.2f", sale.total)} MXN*\n")
        sb.append("📌 Estado: ${if (sale.isPaid) "✅ APLICADO / PAGADO" else "⏳ POR CONFIRMAR / PENDIENTE"}\n")
        sb.append("🙏 ¡Gracias por su compra!")
        return sb.toString()
    }

    fun isToday(timestamp: Long): Boolean {
        val calNow = Calendar.getInstance()
        val calTs = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calNow.get(Calendar.YEAR) == calTs.get(Calendar.YEAR) &&
               calNow.get(Calendar.DAY_OF_YEAR) == calTs.get(Calendar.DAY_OF_YEAR)
    }
}
