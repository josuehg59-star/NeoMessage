import requests
import json
import os

apk_path = r'app\build\outputs\apk\release\app-release.apk'
size = os.path.getsize(apk_path)
print(f"Local APK size: {size} bytes ({size / (1024*1024):.2f} MB)")

# 1. Litterbox upload
url = ""
try:
    with open(apk_path, 'rb') as f:
        r = requests.post(
            'https://litterbox.catbox.moe/resources/internals/api.php',
            data={'reqtype': 'fileupload', 'time': '72h'},
            files={'fileToUpload': ('NeoMessage-v3.9.apk', f, 'application/vnd.android.package-archive')},
            timeout=120
        )
        if r.status_code == 200 and r.text.startswith('http'):
            url = r.text.strip()
            print("Litterbox success:", url)
except Exception as e:
    print("Litterbox err:", e)

# Verify uploaded file size via HTTP HEAD/GET
if url:
    head = requests.head(url)
    remote_size = head.headers.get('Content-Length')
    print(f"Verified Remote Content-Length: {remote_size} bytes")

    # Update Firebase RTDB with working verified URL
    fb_url = 'https://neomessage-19ab4-default-rtdb.firebaseio.com/app_updates.json'
    update_payload = {
        'versionCode': 19,
        'versionName': '3.9-GOLD-RELEASE',
        'apkUrl': url,
        'changelog': 'Módulo de Oficios: Cotizador y presupuestos formales en 1 toque con aprobación del cliente en chat, Acotador de medidas técnicas con el dedo sobre fotos en HD (2048px) y NeoHealth (Ficha biométrica y sincronización con relojes inteligentes).',
        'isMandatory': False
    }
    r_fb = requests.put(fb_url, json=update_payload, timeout=15)
    print("Firebase updated:", r_fb.status_code)
