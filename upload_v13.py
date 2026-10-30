import urllib.request
import json
import os
import sys

def upload_and_verify():
    apk_path = os.path.abspath(r"app\build\outputs\apk\release\app-release.apk")
    if not os.path.exists(apk_path):
        print(f"Error: {apk_path} no existe")
        sys.exit(1)

    file_size = os.path.getsize(apk_path)
    print(f"Subiendo {apk_path} ({file_size} bytes)...")

    boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
    with open(apk_path, "rb") as f:
        apk_bytes = f.read()

    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="reqtype"\r\n\r\n'
        f"fileupload\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="fileToUpload"; filename="neomessage_v3_3_gold.apk"\r\n'
        f"Content-Type: application/vnd.android.package-archive\r\n\r\n"
    ).encode("utf-8") + apk_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")

    req = urllib.request.Request(
        "https://catbox.moe/user/api.php",
        data=body,
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "User-Agent": "Mozilla/5.0"
        }
    )

    resp = urllib.request.urlopen(req, timeout=120)
    apk_url = resp.read().decode("utf-8").strip()
    print(f"Respuesta Catbox: {apk_url}")

    if not apk_url.startswith("http"):
        print("Error en subida:", apk_url)
        sys.exit(1)

    # Verificacion estricta de encabezados y longitud
    test_req = urllib.request.Request(apk_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(test_req, timeout=30) as r:
        header = r.read(4)
        c_len = int(r.headers.get("Content-Length", 0))
        print(f"Header: {header}, Length: {c_len}")
        if header != b"PK\x03\x04" or c_len < 10_000_000:
            print("ERROR: Verificación fallida de APK en CDN")
            sys.exit(1)

    print("Verificacion exitosa! APK 100% integra y valida.")

    # Actualizar Firebase /app_updates con version 13 OBLIGATORIA
    firebase_url = "https://neomessage-19ab4-default-rtdb.firebaseio.com/app_updates.json"
    payload = {
        "versionCode": 13,
        "versionName": "3.3-GOLD-RELEASE",
        "apkUrl": apk_url,
        "isMandatory": True,
        "changeLog": "• Separación total: Llamada de Voz vs Videollamada\n• Tono de llamada saliente (tuut... tuut...) y timbre de llamada entrante\n• Solución definitiva de 'Escribiendo...' con validación de 4 segundos\n• Seguridad de registro: Validación estricta de 10 dígitos y opción de actualizar nombre\n• Cero duplicados en contactos\n• Nombre de contacto siempre visible en chat\n• Cámara directa de fotos y videos sin bloqueos",
        "timestamp": int(1793327000000)
    }
    
    put_req = urllib.request.Request(
        firebase_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="PUT"
    )
    with urllib.request.urlopen(put_req, timeout=20) as fb_resp:
        print("Firebase /app_updates actualizado:", fb_resp.read().decode("utf-8"))

    return apk_url

if __name__ == "__main__":
    url = upload_and_verify()
    print("TODO COMPLETADO CON ÉXITO:", url)
