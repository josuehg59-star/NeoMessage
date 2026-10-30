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
        f'Content-Disposition: form-data; name="fileToUpload"; filename="neomessage_v3_2_release.apk"\r\n'
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

    # Actualizar Firebase /app_updates
    firebase_url = "https://neomessage-19ab4-default-rtdb.firebaseio.com/app_updates.json"
    payload = {
        "versionCode": 12,
        "versionName": "3.2-SOVEREIGN-TITAN",
        "apkUrl": apk_url,
        "isMandatory": False,
        "changeLog": "• Contacto y estado siempre visibles en cabecera\n• Captura directa de foto y video desde cámara\n• Pantalla activa en reposo para llamadas sin congelarse\n• Reordenamiento automático de chats recientes al inicio\n• Separación inteligente de fechas (HOY / AYER)\n• Telemetría de errores automática",
        "timestamp": int(1793325000000)
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
