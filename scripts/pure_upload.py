import urllib.request
import json
import os

apk = r'app\build\outputs\apk\release\app-release.apk'
boundary = '----WebKitFormBoundary7MA4YWxkTrZu0gW'
with open(apk, 'rb') as f:
    data = f.read()

body = bytearray()
body.extend(('--' + boundary + '\r\nContent-Disposition: form-data; name="reqtype"\r\n\r\nfileupload\r\n').encode('utf-8'))
body.extend(('--' + boundary + '\r\nContent-Disposition: form-data; name="time"\r\n\r\n72h\r\n').encode('utf-8'))
body.extend(('--' + boundary + '\r\nContent-Disposition: form-data; name="fileToUpload"; filename="NeoMessage-v3.9.apk"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n').encode('utf-8'))
body.extend(data)
body.extend(('\r\n--' + boundary + '--\r\n').encode('utf-8'))

req = urllib.request.Request('https://litterbox.catbox.moe/resources/internals/api.php', data=body)
req.add_header('Content-Type', f'multipart/form-data; boundary={boundary}')
req.add_header('User-Agent', 'Mozilla/5.0')

try:
    resp = urllib.request.urlopen(req, timeout=120)
    url = resp.read().decode('utf-8').strip()
    print('DIRECT_URL:', url)

    # Actualizar Firebase
    fb_url = 'https://neomessage-19ab4-default-rtdb.firebaseio.com/app_updates.json'
    update_payload = {
        'versionCode': 19,
        'versionName': '3.9-GOLD-RELEASE',
        'apkUrl': url,
        'changelog': 'Módulo de Oficios: Cotizador y presupuestos formales en 1 toque con aprobación del cliente en chat, Acotador de medidas técnicas con el dedo sobre fotos en HD (2048px) y NeoHealth (Ficha biométrica y sincronización con relojes inteligentes).',
        'isMandatory': False
    }
    req_fb = urllib.request.Request(fb_url, data=json.dumps(update_payload).encode('utf-8'), headers={'Content-Type': 'application/json'}, method='PUT')
    urllib.request.urlopen(req_fb, timeout=10)
    print('FIREBASE_OK')
except Exception as e:
    print('ERR:', e)
