import urllib.request
import uuid
import json

apk_path = 'app/build/outputs/apk/debug/app-debug.apk'
boundary = uuid.uuid4().hex

with open(apk_path, 'rb') as f:
    file_bytes = f.read()

body = bytearray()
body.extend(f'--{boundary}\r\n'.encode('utf-8'))
body.extend(b'Content-Disposition: form-data; name="reqtype"\r\n\r\n')
body.extend(b'fileupload\r\n')

body.extend(f'--{boundary}\r\n'.encode('utf-8'))
body.extend(b'Content-Disposition: form-data; name="fileToUpload"; filename="NeoMessage_v2.5.apk"\r\n')
body.extend(b'Content-Type: application/vnd.android.package-archive\r\n\r\n')
body.extend(file_bytes)
body.extend(b'\r\n')

body.extend(f'--{boundary}--\r\n'.encode('utf-8'))

headers = {
    'Content-Type': f'multipart/form-data; boundary={boundary}',
    'User-Agent': 'Mozilla/5.0'
}

print("Uploading 24 MB APK to public fast host...")
req = urllib.request.Request('https://catbox.moe/user/api.php', data=bytes(body), headers=headers)
try:
    with urllib.request.urlopen(req, timeout=120) as resp:
        download_url = resp.read().decode('utf-8').strip()
        print("PUBLIC_APK_URL:", download_url)
        
        # Now update Firebase with the REAL live download URL!
        fb_url = 'https://neomessage-19ab4-default-rtdb.firebaseio.com/app_updates.json'
        update_data = {
            'versionCode': 5,
            'versionName': '2.5-SOVEREIGN-ELITE',
            'apkUrl': download_url,
            'changelog': 'Gran Actualizacion: Reacciones con Emojis, Respuestas citadas, Estados 24h sin errores, Boveda con PIN y Certificado de Autoria Soberana.',
            'isMandatory': False
        }
        fb_req = urllib.request.Request(fb_url, data=json.dumps(update_data).encode('utf-8'), headers={'Content-Type': 'application/json'}, method='PUT')
        with urllib.request.urlopen(fb_req) as fb_resp:
            print("FIREBASE_UPDATED_SUCCESSFULLY!")
except Exception as e:
    print("Upload error:", e)
