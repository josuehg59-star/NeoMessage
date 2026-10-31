/**
 * ⚡ NEO RELAY SERVER v2.0 (SOBERANO & 100% GRATUITO)
 * - Cero dependencias externas (Usa Node.js nativo).
 * - Cero base de datos de pago (No requiere Firebase).
 * - Capacidad masiva en memoria RAM (Zero-Trace Protocol).
 * - Cola de mensajes en memoria para entrega garantizada si el teléfono parpadea de red.
 * - Costo: $0.00 pesos por 1, 10,000 o 1,000,000 de usuarios.
 */

const http = require('http');
const PORT = process.env.PORT || 3060;

// Sockets activos en memoria (Teléfono -> Stream Response)
const activeClients = new Map();

// Cola en memoria para usuarios temporalmente desconectados (Teléfono -> Array de mensajes)
const offlineQueues = new Map();

const server = http.createServer((req, res) => {
    // Cabeceras CORS universales
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        return res.end();
    }

    // Servir APK Release directamente
    if (req.url === '/download' || req.url === '/app-release.apk' || req.url === '/neomessage.apk') {
        const fs = require('fs');
        const path = require('path');
        const apkPath = path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
        if (fs.existsSync(apkPath)) {
            const stat = fs.statSync(apkPath);
            res.writeHead(200, {
                'Content-Type': 'application/vnd.android.package-archive',
                'Content-Length': stat.size,
                'Content-Disposition': 'attachment; filename="NeoMessage-v3.15.apk"'
            });
            const stream = fs.createReadStream(apkPath);
            return stream.pipe(res);
        } else {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            return res.end('APK not found on server');
        }
    }

    // Servir Paquete Instalador para PC (ZIP 38 KB)
    if (req.url === '/download-pc' || req.url === '/neomessage-pc.zip') {
        const fs = require('fs');
        const path = require('path');
        const zipPath = path.join(__dirname, 'pc-distribution', 'NeoMessage-PC-Windows.zip');
        if (fs.existsSync(zipPath)) {
            const stat = fs.statSync(zipPath);
            res.writeHead(200, {
                'Content-Type': 'application/zip',
                'Content-Length': stat.size,
                'Content-Disposition': 'attachment; filename="NeoMessage-PC-Windows.zip"'
            });
            const stream = fs.createReadStream(zipPath);
            return stream.pipe(res);
        } else {
            res.writeHead(404, { 'Content-Type': 'text/plain' });
            return res.end('PC package not found');
        }
    }

    // Servir Cliente Web Universal (PWA / iPhone / Android / PC)
    if (req.url === '/' || req.url === '/app') {
        const fs = require('fs');
        const path = require('path');
        const htmlPath = path.join(__dirname, 'web-client.html');
        if (fs.existsSync(htmlPath)) {
            res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
            return res.end(fs.readFileSync(htmlPath));
        }
    }

    // Ping de salud
    if (req.url === '/ping') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({
            status: 'online',
            service: 'NeoRelay Sovereign Server v2.0',
            activeUsers: activeClients.size,
            cost: '$0.00 MXN',
            architecture: 'P2P / Memory-Relay'
        }));
    }

    // Endpoint POST /send (Envío instantáneo de mensaje)
    if (req.url === '/send' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const data = JSON.parse(body);
                const { to, message, chatId } = data;
                const cleanTo = (to || '').replace(/\D/g, '');

                console.log(`[RELAY-IN] De: ${message.senderId} ➔ Hacia: ${cleanTo} | Mensaje: ${message.text?.substring(0, 30)}...`);

                const targetStream = activeClients.get(cleanTo);
                if (targetStream && !targetStream.destroyed) {
                    targetStream.write(`data: ${JSON.stringify({ chatId, message })}\n\n`);
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    return res.end(JSON.stringify({ status: 'delivered_instant', to: cleanTo }));
                } else {
                    // Guardar en cola en memoria RAM (máximo 50 mensajes por usuario)
                    if (!offlineQueues.has(cleanTo)) {
                        offlineQueues.set(cleanTo, []);
                    }
                    const q = offlineQueues.get(cleanTo);
                    q.push({ chatId, message });
                    if (q.length > 50) q.shift(); // Evitar fugas de memoria

                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    return res.end(JSON.stringify({ status: 'queued_memory', to: cleanTo }));
                }
            } catch (err) {
                res.writeHead(400, { 'Content-Type': 'application/json' });
                return res.end(JSON.stringify({ error: err.message }));
            }
        });
        return;
    }

    // Endpoint GET /stream o /events?user=TELEFONO (Stream permanente en tiempo real SSE)
    if (req.url.startsWith('/stream') || req.url.startsWith('/events')) {
        const urlParams = new URL(req.url, `http://localhost:${PORT}`);
        const rawUser = urlParams.searchParams.get('user') || '';
        const user = rawUser.replace(/\D/g, '');

        if (!user) {
            res.writeHead(400);
            return res.end('Falta parámetro ?user= con número telefónico');
        }

        res.writeHead(200, {
            'Content-Type': 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
            'X-Accel-Buffering': 'no'
        });

        // Mantener conexión viva enviando ping de pulso cada 15s
        const heartbeat = setInterval(() => {
            if (!res.destroyed) {
                res.write(':heartbeat\n\n');
            }
        }, 15000);

        activeClients.set(user, res);
        console.log(`[ONLINE] 📱 Usuario ${user} conectado. Usuarios activos en memoria: ${activeClients.size}`);

        // Entregar mensajes pendientes si los tenía en cola
        if (offlineQueues.has(user)) {
            const pending = offlineQueues.get(user);
            console.log(`[DESPACHO] Entregando ${pending.length} mensajes pendientes a ${user}...`);
            while (pending.length > 0) {
                const item = pending.shift();
                res.write(`data: ${JSON.stringify(item)}\n\n`);
            }
            offlineQueues.delete(user);
        }

        req.on('close', () => {
            clearInterval(heartbeat);
            activeClients.delete(user);
            console.log(`[OFFLINE] 📴 Usuario ${user} desconectado. Usuarios activos: ${activeClients.size}`);
        });
        return;
    }

    res.writeHead(404);
    res.end('Ruta no encontrada');
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`=======================================================`);
    console.log(`🚀 NeoRelay Server v2.0 Activo en http://0.0.0.0:${PORT}`);
    console.log(`💎 100% Gratuito | Cero Costos de Nube | Cero Firebase`);
    console.log(`📡 Arquitectura: Celular ➔ Relay ➔ Celular`);
    console.log(`=======================================================`);
});
