import shutil

path = "index.html"
with open(path, "r", encoding="utf-8") as f:
    html = f.read()

# 1. Add POS Store card in tabQuotes sidebar
old_tab_quotes = """                    <div style="background: var(--bg-panel); border: 1px solid var(--border); border-radius: 12px; padding: 16px;">
                        <h4 style="color: var(--neon-green); margin-bottom: 8px;">📌 Mis Notas Personales</h4>
                        <p style="font-size: 12px; color: var(--text-secondary); margin-bottom: 12px;">Tu espacio privado para recordatorios, textos e imágenes.</p>
                        <button class="btn-primary" onclick="openSelfChat()">Abrir Mis Notas</button>
                    </div>"""

new_tab_quotes = """                    <div style="background: var(--bg-panel); border: 1px solid rgba(0,255,127,0.4); border-radius: 12px; padding: 16px; margin-bottom: 12px;">
                        <h4 style="color: #00FF7F; margin-bottom: 8px;">🛒 POS Tienda & Clientes (Opcional)</h4>
                        <p style="font-size: 12px; color: var(--text-secondary); margin-bottom: 14px;">Marca productos de tu catálogo, asigna clientes, envía tickets y realiza tu Corte del Día (solo lo aplicado entra al corte, 0 MB en Firebase).</p>
                        <button class="btn-primary" style="background: #00FF7F; color: #000;" onclick="openPosStoreModal()">🏪 Abrir Punto de Venta (POS)</button>
                    </div>

                    <div style="background: var(--bg-panel); border: 1px solid var(--border); border-radius: 12px; padding: 16px;">
                        <h4 style="color: var(--neon-green); margin-bottom: 8px;">📌 Mis Notas Personales</h4>
                        <p style="font-size: 12px; color: var(--text-secondary); margin-bottom: 12px;">Tu espacio privado para recordatorios, textos e imágenes.</p>
                        <button class="btn-primary" onclick="openSelfChat()">Abrir Mis Notas</button>
                    </div>"""

assert old_tab_quotes in html, "old_tab_quotes not found in index.html"
html = html.replace(old_tab_quotes, new_tab_quotes, 1)

# 2. Add POS Store Modal + JS before </body>
pos_modal_and_js = """
    <!-- MODAL: PUNTO DE VENTA (POS BÁSICO OPCIONAL) CON CATÁLOGO, CLIENTES Y CORTE DEL DÍA -->
    <div id="posStoreModal" class="modal-overlay" style="display: none; z-index: 100020;">
        <div class="dialog-card" style="max-width: 760px; width: 95%; max-height: 90vh; display: flex; flex-direction: column; padding: 18px;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 12px;">
                <div>
                    <h3 style="color: #00FF7F; margin: 0; font-size: 18px; display:flex; align-items:center; gap:8px;">
                        <span>🛒</span> Punto de Venta (POS Básico) & Control de Tienda
                    </h3>
                    <div style="font-size:11px; color:#94A3B8; margin-top:2px;">100% Memoria Local (0 KB en Firebase) • Solo los tickets aplicados entran al Corte del Día</div>
                </div>
                <button onclick="document.getElementById('posStoreModal').style.display='none'" style="background:transparent; border:none; color:#AAA; font-size:20px; cursor:pointer;">✕</button>
            </div>

            <!-- SUB-TABS POS -->
            <div style="display:flex; gap:8px; margin-bottom:12px;">
                <button id="posTabBtn0" onclick="switchPosWebTab(0)" style="flex:1; padding:9px; border-radius:8px; border:1px solid #00FF7F; background:#00FF7F; color:#000; font-weight:bold; font-size:12px; cursor:pointer;">🛍️ Nueva Venta</button>
                <button id="posTabBtn1" onclick="switchPosWebTab(1)" style="flex:1; padding:9px; border-radius:8px; border:1px solid #334155; background:#1E293B; color:#FFF; font-weight:bold; font-size:12px; cursor:pointer;">👥 Clientes</button>
                <button id="posTabBtn2" onclick="switchPosWebTab(2)" style="flex:1; padding:9px; border-radius:8px; border:1px solid #334155; background:#1E293B; color:#FFF; font-weight:bold; font-size:12px; cursor:pointer;">📦 Catálogo</button>
                <button id="posTabBtn3" onclick="switchPosWebTab(3)" style="flex:1; padding:9px; border-radius:8px; border:1px solid #334155; background:#1E293B; color:#FFF; font-weight:bold; font-size:12px; cursor:pointer;">📊 Corte del Día</button>
            </div>

            <!-- CONTENIDO PESTAÑA 0: NUEVA VENTA -->
            <div id="posWebPane0" style="display:flex; flex-direction:column; flex:1; overflow:hidden;">
                <div style="background:#0F172A; border:1px solid #334155; border-radius:10px; padding:10px; margin-bottom:10px; display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap;">
                    <div style="display:flex; align-items:center; gap:8px; flex:1;">
                        <span style="color:#FFD700; font-weight:bold; font-size:13px;">👤 Cliente:</span>
                        <select id="posCustomerSelect" style="flex:1; background:#1E293B; color:#FFF; border:1px solid #475569; border-radius:6px; padding:6px 10px; font-size:13px;"></select>
                    </div>
                    <button onclick="promptCreatePosCustomerWeb()" style="background:#10B981; color:#000; border:none; padding:6px 12px; border-radius:6px; font-weight:bold; font-size:12px; cursor:pointer;">➕ Nuevo Cliente</button>
                </div>

                <input type="text" id="posProductSearchWeb" oninput="renderPosCatalogSaleWeb()" placeholder="🔍 Buscar producto o código del catálogo para marcar..." style="width:100%; background:#0F172A; border:1px solid #334155; border-radius:8px; padding:9px 12px; color:#FFF; font-size:13px; margin-bottom:8px;">

                <div id="posProductsSaleListWeb" style="flex:1; overflow-y:auto; max-height:280px; display:flex; flex-direction:column; gap:6px; padding-right:4px;"></div>

                <div style="background:#0F172A; border:1px solid #00FF7F; border-radius:12px; padding:12px; margin-top:10px;">
                    <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;">
                        <div>
                            <div id="posCartCountWeb" style="font-size:12px; color:#94A3B8;">0 artículos marcados</div>
                            <div id="posCartTotalWeb" style="font-size:18px; font-weight:900; color:#00FF7F;">TOTAL: $0.00 MXN</div>
                        </div>
                        <div style="display:flex; align-items:center; gap:8px;">
                            <label style="font-size:12px; color:#FFF; display:flex; align-items:center; gap:4px; cursor:pointer;">
                                <input type="checkbox" id="posApplyToCutCheckbox" checked>
                                <span>✅ Aplicar al Corte (Pagado)</span>
                            </label>
                        </div>
                        <div style="display:flex; gap:8px;">
                            <button onclick="submitPosSaleWeb(false)" style="background:#00FF7F; color:#000; border:none; padding:9px 14px; border-radius:8px; font-weight:bold; font-size:12px; cursor:pointer;">💾 Registrar Venta</button>
                            <button onclick="submitPosSaleWeb(true)" style="background:#FFD700; color:#000; border:none; padding:9px 14px; border-radius:8px; font-weight:bold; font-size:12px; cursor:pointer;">📲 Guardar + Enviar Ticket</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- CONTENIDO PESTAÑA 1: CLIENTES -->
            <div id="posWebPane1" style="display:none; flex-direction:column; flex:1; overflow:hidden;">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
                    <span style="color:#FFF; font-weight:bold; font-size:14px;">Cartera de Clientes</span>
                    <button onclick="promptCreatePosCustomerWeb()" style="background:#00FF7F; color:#000; border:none; padding:7px 14px; border-radius:8px; font-weight:bold; font-size:12px; cursor:pointer;">➕ Crear Cliente</button>
                </div>
                <div id="posCustomersListWeb" style="flex:1; overflow-y:auto; max-height:380px; display:flex; flex-direction:column; gap:8px;"></div>
            </div>

            <!-- CONTENIDO PESTAÑA 2: CATÁLOGO -->
            <div id="posWebPane2" style="display:none; flex-direction:column; flex:1; overflow:hidden;">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
                    <span style="color:#FFF; font-weight:bold; font-size:14px;">Catálogo de Productos de tu Tienda</span>
                    <button onclick="promptAddPosProductWeb()" style="background:#00FF7F; color:#000; border:none; padding:7px 14px; border-radius:8px; font-weight:bold; font-size:12px; cursor:pointer;">➕ Añadir Producto</button>
                </div>
                <div id="posCatalogManageListWeb" style="flex:1; overflow-y:auto; max-height:380px; display:flex; flex-direction:column; gap:6px;"></div>
            </div>

            <!-- CONTENIDO PESTAÑA 3: CORTE DEL DÍA -->
            <div id="posWebPane3" style="display:none; flex-direction:column; flex:1; overflow:hidden;">
                <div style="display:flex; gap:10px; margin-bottom:10px;">
                    <div style="flex:1; background:#0F172A; border:1px solid #00FF7F; border-radius:10px; padding:10px;">
                        <div style="font-size:11px; color:#94A3B8;">Corte de Hoy (Aplicado)</div>
                        <div id="posCutTodayWeb" style="font-size:18px; font-weight:900; color:#00FF7F;">$0.00 MXN</div>
                    </div>
                    <div style="flex:1; background:#0F172A; border:1px solid #334155; border-radius:10px; padding:10px;">
                        <div style="font-size:11px; color:#94A3B8;">Total Aplicado General</div>
                        <div id="posCutAllPaidWeb" style="font-size:18px; font-weight:bold; color:#FFF;">$0.00 MXN</div>
                    </div>
                    <div style="flex:1; background:#0F172A; border:1px solid #F59E0B; border-radius:10px; padding:10px;">
                        <div style="font-size:11px; color:#94A3B8;">Sin Aplicar / Pendientes</div>
                        <div id="posCutPendingWeb" style="font-size:18px; font-weight:bold; color:#F59E0B;">$0.00 MXN</div>
                    </div>
                </div>
                <div style="font-size:11px; color:#CBD5E1; margin-bottom:8px;">
                    💡 <b>Nota:</b> Los tickets que <b>NO se vendan</b> pueden eliminarse con <b>🗑️ Eliminar</b>. Solo los tickets con <b>✅ En Corte</b> suman al Corte del Día.
                </div>
                <div id="posSalesHistoryListWeb" style="flex:1; overflow-y:auto; max-height:320px; display:flex; flex-direction:column; gap:8px;"></div>
            </div>
        </div>
    </div>

    <script>
        const DEFAULT_POS_CATALOG_WEB = [
            { codigo: 1, nombre: "IMPRESIONES B/N", precio: "1" },
            { codigo: 2, nombre: "IMPRESIONES A COLOR LETRAS", precio: "2" },
            { codigo: 3, nombre: "IMPRESIONES A COLOR IMÁGENES GDES", precio: "5" },
            { codigo: 4, nombre: "CARGA DE BATERIA COCHE O MOTO", precio: "50" },
            { codigo: 5, nombre: "ACEITE NUTRIOLI COMESTIBLE PURO DE SOYA 400 ML", precio: "24" },
            { codigo: 6, nombre: "ACEITE NUTRIOLI COMESTIBLE PURO DE SOYA 850 ML", precio: "44" },
            { codigo: 7, nombre: "AGUA CIEL PURIFICADA BOTTLE 1 LTS NR", precio: "16" },
            { codigo: 8, nombre: "AGUA CRISTAL PURIFICADA 20 LTS", precio: "50" },
            { codigo: 9, nombre: "AZUCAR KILO", precio: "22" },
            { codigo: 10, nombre: "COCA COLA 600 ML", precio: "18" },
            { codigo: 11, nombre: "COCA COLA 2.5 LTS", precio: "38" },
            { codigo: 12, nombre: "HUEVO CONO 30 PZAS", precio: "85" },
            { codigo: 13, nombre: "LECHE LALA ENTERA 1 LTS", precio: "26" }
        ];

        let posCartMapWeb = {}; // idx -> qty

        function getPosCatalogWeb() {
            try {
                const raw = localStorage.getItem('neo_pos_catalog');
                if (raw) {
                    const parsed = JSON.parse(raw);
                    if (Array.isArray(parsed) && parsed.length > 0) return parsed;
                }
            } catch (_) {}
            return DEFAULT_POS_CATALOG_WEB.slice();
        }

        function savePosCatalogWeb(list) {
            try { localStorage.setItem('neo_pos_catalog', JSON.stringify(list)); } catch (_) {}
        }

        function getPosCustomersWeb() {
            try {
                const raw = localStorage.getItem('neo_pos_customers');
                return raw ? JSON.parse(raw) : [];
            } catch (_) { return []; }
        }

        function savePosCustomersWeb(list) {
            try { localStorage.setItem('neo_pos_customers', JSON.stringify(list)); } catch (_) {}
        }

        function getPosSalesWeb() {
            try {
                const raw = localStorage.getItem('neo_pos_sales');
                return raw ? JSON.parse(raw) : [];
            } catch (_) { return []; }
        }

        function savePosSalesWeb(list) {
            try { localStorage.setItem('neo_pos_sales', JSON.stringify(list.slice(0, 500))); } catch (_) {}
        }

        function openPosStoreModal() {
            // Sincronizar catálogo del bot desde Firebase 1 sola vez si existe
            const cleanMy10 = String(myPhone || '').replace(/\\D/g, '').slice(-10);
            if (cleanMy10) {
                db.ref(`bot_configs/${cleanMy10}/catalogo`).once('value', snap => {
                    const cat = snap.val();
                    if (Array.isArray(cat) && cat.length > 0) {
                        savePosCatalogWeb(cat);
                        renderPosCatalogSaleWeb();
                        renderPosCatalogManageWeb();
                    }
                });
            }
            document.getElementById('posStoreModal').style.display = 'flex';
            switchPosWebTab(0);
        }

        function switchPosWebTab(idx) {
            for (let i = 0; i < 4; i++) {
                const pane = document.getElementById('posWebPane' + i);
                const btn = document.getElementById('posTabBtn' + i);
                if (pane) pane.style.display = (i === idx) ? 'flex' : 'none';
                if (btn) {
                    btn.style.background = (i === idx) ? '#00FF7F' : '#1E293B';
                    btn.style.color = (i === idx) ? '#000' : '#FFF';
                    btn.style.borderColor = (i === idx) ? '#00FF7F' : '#334155';
                }
            }
            refreshPosCustomerSelectWeb();
            if (idx === 0) renderPosCatalogSaleWeb();
            else if (idx === 1) renderPosCustomersListWeb();
            else if (idx === 2) renderPosCatalogManageWeb();
            else if (idx === 3) renderPosSalesCutWeb();
        }

        function refreshPosCustomerSelectWeb() {
            const sel = document.getElementById('posCustomerSelect');
            if (!sel) return;
            const currentVal = sel.value;
            const custs = getPosCustomersWeb();
            sel.innerHTML = '<option value="mostrador">Público en General (Mostrador)</option>';
            custs.forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = c.phone ? `${c.name} (📱 ${c.phone})` : c.name;
                sel.appendChild(opt);
            });
            if (currentVal && Array.from(sel.options).some(o => o.value === currentVal)) {
                sel.value = currentVal;
            }
        }

        function promptCreatePosCustomerWeb() {
            const name = prompt('👤 Nombre del Cliente:');
            if (!name || !name.trim()) return;
            const phone = prompt('📱 Teléfono de 10 dígitos (opcional, para enviarle tickets al chat):', '') || '';
            const notes = prompt('📝 Nota o Dirección (opcional):', '') || '';
            const custs = getPosCustomersWeb();
            const newC = {
                id: 'cli_' + Date.now(),
                name: name.trim(),
                phone: phone.replace(/\\D/g, '').slice(-10),
                notes: notes.trim(),
                createdAt: Date.now()
            };
            custs.push(newC);
            savePosCustomersWeb(custs);
            refreshPosCustomerSelectWeb();
            const sel = document.getElementById('posCustomerSelect');
            if (sel) sel.value = newC.id;
            renderPosCustomersListWeb();
            showToast(`✅ Cliente "${newC.name}" creado`);
        }

        function changePosQtyWeb(idx, delta) {
            const cur = posCartMapWeb[idx] || 0;
            const next = cur + delta;
            if (next <= 0) delete posCartMapWeb[idx];
            else posCartMapWeb[idx] = next;
            renderPosCatalogSaleWeb();
        }

        function renderPosCatalogSaleWeb() {
            const container = document.getElementById('posProductsSaleListWeb');
            if (!container) return;
            const q = (document.getElementById('posProductSearchWeb').value || '').toLowerCase().trim();
            const catalog = getPosCatalogWeb();
            container.innerHTML = '';

            let totalItems = 0;
            let totalAmount = 0;

            catalog.forEach((p, idx) => {
                const qty = posCartMapWeb[idx] || 0;
                const unitPrice = parseFloat(String(p.precio || '0').replace(/[^0-9.]/g, '')) || 0;
                if (qty > 0) {
                    totalItems += qty;
                    totalAmount += qty * unitPrice;
                }
                const code = p.codigo || (idx + 1);
                if (q && !String(p.nombre || '').toLowerCase().includes(q) && !String(code).includes(q)) return;

                const row = document.createElement('div');
                row.style.cssText = `display:flex; justify-content:space-between; align-items:center; padding:8px 12px; border-radius:8px; background:${qty > 0 ? '#064E3B' : '#1E293B'}; border:1px solid ${qty > 0 ? '#00FF7F' : '#334155'};`;
                row.innerHTML = `
                    <div>
                        <div style="color:#FFF; font-weight:bold; font-size:13px;">[${code}] ${escapeHtml(p.nombre)}</div>
                        <div style="color:#00FF7F; font-weight:bold; font-size:12px;">$${unitPrice.toFixed(2)} MXN</div>
                    </div>
                    <div style="display:flex; align-items:center; gap:8px;">
                        <button onclick="changePosQtyWeb(${idx}, -1)" style="width:28px; height:28px; border-radius:6px; border:none; background:#334155; color:#FFF; font-weight:bold; cursor:pointer;">-</button>
                        <span style="min-width:22px; text-align:center; color:${qty > 0 ? '#00FF7F' : '#94A3B8'}; font-weight:bold; font-size:14px;">${qty}</span>
                        <button onclick="changePosQtyWeb(${idx}, 1)" style="width:28px; height:28px; border-radius:6px; border:none; background:#00FF7F; color:#000; font-weight:bold; cursor:pointer;">+</button>
                    </div>
                `;
                container.appendChild(row);
            });

            document.getElementById('posCartCountWeb').innerText = `${totalItems} artículo(s) marcados`;
            document.getElementById('posCartTotalWeb').innerText = `TOTAL: $${totalAmount.toFixed(2)} MXN`;
        }

        function submitPosSaleWeb(sendToChat) {
            const catalog = getPosCatalogWeb();
            const items = [];
            Object.keys(posCartMapWeb).forEach(k => {
                const idx = parseInt(k);
                const qty = posCartMapWeb[idx] || 0;
                const p = catalog[idx];
                if (p && qty > 0) {
                    const price = parseFloat(String(p.precio || '0').replace(/[^0-9.]/g, '')) || 0;
                    items.push({
                        codigo: p.codigo || (idx + 1),
                        nombre: p.nombre,
                        precioUnitario: price,
                        cantidad: qty,
                        subtotal: price * qty
                    });
                }
            });

            if (items.length === 0) {
                alert('Marca al menos 1 producto del catálogo con el botón [+] para registrar la venta.');
                return;
            }

            const selVal = document.getElementById('posCustomerSelect').value;
            const custs = getPosCustomersWeb();
            const cust = custs.find(c => c.id === selVal) || null;
            const isPaid = document.getElementById('posApplyToCutCheckbox').checked;
            const total = items.reduce((acc, it) => acc + it.subtotal, 0);
            const sales = getPosSalesWeb();
            const folio = '#POS-' + String(sales.length + 1).padStart(4, '0');

            const record = {
                id: 'sale_' + Date.now(),
                folio: folio,
                customerId: cust ? cust.id : 'mostrador',
                customerName: cust ? cust.name : 'Público en General',
                customerPhone: cust ? (cust.phone || '') : '',
                items: items,
                total: total,
                isPaid: isPaid,
                timestamp: Date.now()
            };

            sales.unshift(record);
            savePosSalesWeb(sales);
            posCartMapWeb = {};
            renderPosCatalogSaleWeb();
            showToast(`✅ Venta ${folio} registrada ($${total.toFixed(2)} MXN)`);

            if (sendToChat) {
                const targetPhone = record.customerPhone || currentRecipient;
                if (!targetPhone) {
                    alert('Venta guardada. Para enviarla por chat, selecciona un cliente con teléfono o abre primero el chat del cliente.');
                    return;
                }
                const p10 = String(targetPhone).replace(/\\D/g, '').slice(-10);
                const my10 = String(myPhone || '').replace(/\\D/g, '').slice(-10);
                const ids = [my10, p10].sort();
                const tChatId = `private_${ids[0]}_${ids[1]}`;
                let ticket = `🧾 *NOTA DE VENTA ${record.folio}*\\n👤 Cliente: *${record.customerName}*\\n━━━━━━━━━━━━━━━━━━━━\\n`;
                record.items.forEach(it => {
                    ticket += `• ${it.cantidad}x ${it.nombre} ($${it.precioUnitario.toFixed(2)}) = *$${it.subtotal.toFixed(2)}*\\n`;
                });
                ticket += `━━━━━━━━━━━━━━━━━━━━\\n💰 *TOTAL: $${record.total.toFixed(2)} MXN*\\n📌 Estado: ${record.isPaid ? '✅ PAGADO' : '⏳ PENDIENTE POR COBRAR'}\\n🙏 ¡Gracias por su compra!`;

                selectChat({
                    chatId: tChatId,
                    recipientPhone: p10,
                    name: record.customerName,
                    lastText: ticket,
                    timestamp: Date.now(),
                    isSelf: false
                });
                dispatchMessageObject(ticket);
                document.getElementById('posStoreModal').style.display = 'none';
            }
        }

        function renderPosCustomersListWeb() {
            const container = document.getElementById('posCustomersListWeb');
            if (!container) return;
            const custs = getPosCustomersWeb();
            const sales = getPosSalesWeb();
            container.innerHTML = '';

            if (custs.length === 0) {
                container.innerHTML = '<div style="text-align:center; padding:30px; color:#64748B;">No hay clientes registrados. Haz clic en "➕ Crear Cliente".</div>';
                return;
            }

            custs.forEach(c => {
                const cSales = sales.filter(s => s.customerId === c.id);
                const totalBought = cSales.reduce((a, b) => a + (b.total || 0), 0);
                const pending = cSales.filter(s => !s.isPaid).reduce((a, b) => a + (b.total || 0), 0);
                const prodsSummary = cSales.slice(0, 3).map(s => `${s.folio}: ` + (s.items || []).map(i => `${i.cantidad}x ${i.nombre}`).join(', ')).join(' | ');

                const card = document.createElement('div');
                card.style.cssText = 'background:#1E293B; border:1px solid #334155; border-radius:10px; padding:12px; display:flex; justify-content:space-between; align-items:center; gap:10px;';
                card.innerHTML = `
                    <div style="flex:1;">
                        <div style="color:#FFF; font-weight:bold; font-size:14px;">👤 ${escapeHtml(c.name)} ${c.phone ? `<span style="color:#00FF7F; font-size:12px;">📱 ${escapeHtml(c.phone)}</span>` : ''}</div>
                        ${c.notes ? `<div style="color:#94A3B8; font-size:12px;">📝 ${escapeHtml(c.notes)}</div>` : ''}
                        <div style="color:#CBD5E1; font-size:12px; margin-top:4px;">🛍️ ${cSales.length} compra(s) • Total: <b>$${totalBought.toFixed(2)} MXN</b> ${pending > 0 ? `• <span style="color:#F59E0B; font-weight:bold;">⏳ Pendiente: $${pending.toFixed(2)}</span>` : ''}</div>
                        ${prodsSummary ? `<div style="color:#94A3B8; font-size:11px; margin-top:3px;">📦 Últimas compras: ${escapeHtml(prodsSummary)}</div>` : ''}
                    </div>
                    <div style="display:flex; gap:6px;">
                        <button onclick="document.getElementById('posCustomerSelect').value='${c.id}'; switchPosWebTab(0);" style="background:#00FF7F; color:#000; border:none; padding:6px 10px; border-radius:6px; font-weight:bold; font-size:11px; cursor:pointer;">Venderle</button>
                        <button onclick="deletePosCustomerWeb('${c.id}')" style="background:#EF4444; color:#FFF; border:none; padding:6px 10px; border-radius:6px; font-weight:bold; font-size:11px; cursor:pointer;">🗑️</button>
                    </div>
                `;
                container.appendChild(card);
            });
        }

        function deletePosCustomerWeb(id) {
            const custs = getPosCustomersWeb().filter(c => c.id !== id);
            savePosCustomersWeb(custs);
            renderPosCustomersListWeb();
            refreshPosCustomerSelectWeb();
        }

        function promptAddPosProductWeb() {
            const name = prompt('📦 Nombre del Producto o Servicio:');
            if (!name || !name.trim()) return;
            const price = prompt('💲 Precio en MXN (ej: 25):', '0');
            if (price === null) return;
            const catalog = getPosCatalogWeb();
            catalog.push({
                codigo: catalog.length + 1,
                nombre: name.trim().toUpperCase(),
                precio: String(parseFloat(price) || 0)
            });
            savePosCatalogWeb(catalog);
            renderPosCatalogManageWeb();
            showToast('✅ Producto añadido al catálogo');
        }

        function renderPosCatalogManageWeb() {
            const container = document.getElementById('posCatalogManageListWeb');
            if (!container) return;
            const catalog = getPosCatalogWeb();
            container.innerHTML = '';
            catalog.forEach((p, idx) => {
                const row = document.createElement('div');
                row.style.cssText = 'background:#1E293B; border:1px solid #334155; border-radius:8px; padding:8px 12px; display:flex; justify-content:space-between; align-items:center;';
                row.innerHTML = `
                    <div>
                        <span style="color:#FFF; font-weight:bold; font-size:13px;">[${p.codigo || (idx + 1)}] ${escapeHtml(p.nombre)}</span>
                        <span style="color:#00FF7F; font-weight:bold; font-size:13px; margin-left:8px;">$${escapeHtml(String(p.precio))} MXN</span>
                    </div>
                    <button onclick="deletePosProductWeb(${idx})" style="background:rgba(239,68,68,0.2); color:#EF4444; border:1px solid #EF4444; padding:4px 8px; border-radius:6px; font-size:11px; cursor:pointer;">🗑️ Eliminar</button>
                `;
                container.appendChild(row);
            });
        }

        function deletePosProductWeb(idx) {
            const catalog = getPosCatalogWeb();
            catalog.splice(idx, 1);
            savePosCatalogWeb(catalog);
            renderPosCatalogManageWeb();
        }

        function renderPosSalesCutWeb() {
            const container = document.getElementById('posSalesHistoryListWeb');
            if (!container) return;
            const sales = getPosSalesWeb();
            const nowStr = new Date().toDateString();

            // SOLO LOS TICKETS APLICADOS (isPaid === true) ENTRAN AL CORTE DEL DÍA
            const todayApplied = sales.filter(s => new Date(s.timestamp).toDateString() === nowStr && s.isPaid);
            const todayCut = todayApplied.reduce((a, b) => a + (b.total || 0), 0);
            const allApplied = sales.filter(s => s.isPaid).reduce((a, b) => a + (b.total || 0), 0);
            const allPending = sales.filter(s => !s.isPaid).reduce((a, b) => a + (b.total || 0), 0);

            document.getElementById('posCutTodayWeb').innerText = `$${todayCut.toFixed(2)} MXN`;
            document.getElementById('posCutAllPaidWeb').innerText = `$${allApplied.toFixed(2)} MXN`;
            document.getElementById('posCutPendingWeb').innerText = `$${allPending.toFixed(2)} MXN`;

            container.innerHTML = '';
            if (sales.length === 0) {
                container.innerHTML = '<div style="text-align:center; padding:30px; color:#64748B;">No hay ventas registradas aún.</div>';
                return;
            }

            sales.forEach(s => {
                const itemsText = (s.items || []).map(i => `${i.cantidad}x ${i.nombre} ($${Number(i.subtotal || 0).toFixed(2)})`).join(' • ');
                const card = document.createElement('div');
                card.style.cssText = `background:#1E293B; border:1px solid ${s.isPaid ? 'rgba(0,255,127,0.4)' : 'rgba(245,158,11,0.5)'}; border-radius:10px; padding:10px 12px;`;
                card.innerHTML = `
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                        <span style="color:#FFD700; font-weight:bold; font-size:13px;">${escapeHtml(s.folio)} • 👤 ${escapeHtml(s.customerName)}</span>
                        <span style="color:#94A3B8; font-size:11px;">${new Date(s.timestamp).toLocaleString()}</span>
                    </div>
                    <div style="color:#CBD5E1; font-size:12px; margin-bottom:8px;">${escapeHtml(itemsText)}</div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="color:${s.isPaid ? '#00FF7F' : '#F59E0B'}; font-weight:900; font-size:14px;">TOTAL: $${Number(s.total || 0).toFixed(2)} MXN</span>
                        <div style="display:flex; gap:6px;">
                            <button onclick="togglePosSaleAppliedWeb('${s.id}')" style="background:${s.isPaid ? '#065F46' : '#00FF7F'}; color:${s.isPaid ? '#FFF' : '#000'}; border:none; padding:5px 10px; border-radius:6px; font-weight:bold; font-size:11px; cursor:pointer;">
                                ${s.isPaid ? '✅ En Corte' : '✅ Aplicar al Corte'}
                            </button>
                            <button onclick="deletePosSaleWeb('${s.id}')" style="background:rgba(239,68,68,0.15); color:#EF4444; border:1px solid #EF4444; padding:5px 10px; border-radius:6px; font-weight:bold; font-size:11px; cursor:pointer;">
                                🗑️ Eliminar No Vendido
                            </button>
                        </div>
                    </div>
                `;
                container.appendChild(card);
            });
        }

        function togglePosSaleAppliedWeb(saleId) {
            const sales = getPosSalesWeb().map(s => {
                if (s.id === saleId) s.isPaid = !s.isPaid;
                return s;
            });
            savePosSalesWeb(sales);
            renderPosSalesCutWeb();
        }

        function deletePosSaleWeb(saleId) {
            const sales = getPosSalesWeb().filter(s => s.id !== saleId);
            savePosSalesWeb(sales);
            renderPosSalesCutWeb();
            showToast('🗑️ Ticket eliminado (no entra al Corte del Día)');
        }
    </script>
"""

idx_last_body = html.rfind("</body>")
assert idx_last_body != -1, "</body> not found in index.html"
html = html[:idx_last_body] + pos_modal_and_js + "\n" + html[idx_last_body:]

with open(path, "w", encoding="utf-8") as f:
    f.write(html)

for target in ["public-web/index.html", "web-client.html"]:
    shutil.copyfile(path, target)

print("Added POS Store, Customers, Catalog & Corte del Dia to PC Web successfully!")
