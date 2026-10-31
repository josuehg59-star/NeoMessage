"""
Script Oficial de Publicacion y Actualizacion Automatica de NeoMessage
Ejecucion: python publish_update.py
"""
import ctypes, ctypes.wintypes, urllib.request, json, os, re, sys

class CREDENTIAL(ctypes.Structure):
    _fields_ = [
        ('Flags', ctypes.wintypes.DWORD),
        ('Type', ctypes.wintypes.DWORD),
        ('TargetName', ctypes.wintypes.LPWSTR),
        ('Comment', ctypes.wintypes.LPWSTR),
        ('LastWritten', ctypes.wintypes.FILETIME),
        ('CredentialBlobSize', ctypes.wintypes.DWORD),
        ('CredentialBlob', ctypes.POINTER(ctypes.c_char)),
        ('Persist', ctypes.wintypes.DWORD),
        ('AttributeCount', ctypes.wintypes.DWORD),
        ('Attributes', ctypes.c_void_p),
        ('TargetAlias', ctypes.wintypes.LPWSTR),
        ('UserName', ctypes.wintypes.LPWSTR)
    ]

Advapi32 = ctypes.WinDLL('Advapi32.dll')
CredRead = Advapi32.CredReadW
CredRead.restype = ctypes.wintypes.BOOL
CredRead.argtypes = [ctypes.wintypes.LPCWSTR, ctypes.wintypes.DWORD, ctypes.wintypes.DWORD, ctypes.POINTER(ctypes.POINTER(CREDENTIAL))]

def get_token():
    pCred = ctypes.POINTER(CREDENTIAL)()
    if CredRead('git:https://github.com', 1, 0, ctypes.byref(pCred)):
        return ctypes.string_at(pCred.contents.CredentialBlob, pCred.contents.CredentialBlobSize).decode('utf-16le').strip()
    return None

def main():
    token = get_token()
    if not token:
        print("[ERROR] No se encontro token de GitHub en Windows Credential Manager.")
        return

    gradle_path = r'app\build.gradle'
    with open(gradle_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    code_match = re.search(r'versionCode\s+(\d+)', content)
    name_match = re.search(r'versionName\s+"([^"]+)"', content)
    
    version_code = int(code_match.group(1)) if code_match else 26
    version_name = name_match.group(1) if name_match else "3.16"
    tag_name = f"v{version_name.split('-')[0]}"
    
    print(f"[*] Iniciando publicacion automatica de NeoMessage {tag_name} (Code: {version_code})...")

    headers = {
        'Authorization': f'Bearer {token}',
        'User-Agent': 'NeoMessage-AutoDeploy',
        'Accept': 'application/vnd.github.v3+json'
    }
    owner = 'josuehg59-star'
    repo = 'NeoMessage'

    # 1. Obtener o Crear Release
    release_id = None
    upload_url = None
    try:
        req = urllib.request.Request(
            f'https://api.github.com/repos/{owner}/{repo}/releases',
            data=json.dumps({
                'tag_name': tag_name,
                'name': f'NeoMessage {tag_name}',
                'body': f'Actualizacion oficial de NeoMessage {tag_name}',
                'draft': False,
                'prerelease': False
            }).encode('utf-8'),
            headers={**headers, 'Content-Type': 'application/json'},
            method='POST'
        )
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            release_id = data['id']
            upload_url = data['upload_url'].split('{')[0]
            print(f"[OK] Release {tag_name} creada en GitHub (ID: {release_id})")
    except urllib.error.HTTPError as e:
        if e.code == 422:
            req_get = urllib.request.Request(f'https://api.github.com/repos/{owner}/{repo}/releases/tags/{tag_name}', headers=headers)
            with urllib.request.urlopen(req_get) as resp_get:
                data = json.loads(resp_get.read().decode('utf-8'))
                release_id = data['id']
                upload_url = data['upload_url'].split('{')[0]
                print(f"[INFO] Release {tag_name} ya existia (ID: {release_id})")
        else:
            raise

    # 2. Subir APK
    apk_file = f"NeoMessage-{tag_name}.apk"
    apk_path = r"app\build\outputs\apk\release\app-release.apk"
    if not os.path.exists(apk_path):
        apk_path = os.path.join(r"C:\Users\JOSUE\Desktop", apk_file)

    # Eliminar asset viejo si ya existia en GitHub para reemplazarlo con el recien compilado
    for a in data.get('assets', []):
        if a['name'] == apk_file:
            print(f"[*] Reemplazando APK en GitHub (Borrando asset {a['id']})...")
            try:
                del_req = urllib.request.Request(f"https://api.github.com/repos/{owner}/{repo}/releases/assets/{a['id']}", headers=headers, method='DELETE')
                urllib.request.urlopen(del_req)
                print("[OK] Asset anterior eliminado.")
            except Exception as de:
                print(f"[WARN] No se pudo borrar asset anterior: {de}")

    if os.path.exists(apk_path):
        print(f"[*] Subiendo APK recien compilado ({os.path.getsize(apk_path) / (1024*1024):.2f} MB)...")
        with open(apk_path, 'rb') as f:
            apk_bytes = f.read()
        try:
            req_apk = urllib.request.Request(
                f'{upload_url}?name={apk_file}',
                data=apk_bytes,
                headers={**headers, 'Content-Type': 'application/vnd.android.package-archive'},
                method='POST'
            )
            with urllib.request.urlopen(req_apk):
                print(f"[OK] {apk_file} subido a GitHub exitosamente.")
        except urllib.error.HTTPError as e:
            print(f"[WARN] Error subiendo APK: {e.code}")

    # 3. Subir PC Package
    pc_path = r"C:\Users\JOSUE\Desktop\NeoMessage-PC-Windows.zip"
    if os.path.exists(pc_path):
        print("[*] Subiendo Instalador PC Windows...")
        with open(pc_path, 'rb') as f:
            pc_bytes = f.read()
        try:
            req_pc = urllib.request.Request(
                f'{upload_url}?name=NeoMessage-PC-Windows.zip',
                data=pc_bytes,
                headers={**headers, 'Content-Type': 'application/zip'},
                method='POST'
            )
            with urllib.request.urlopen(req_pc):
                print("[OK] NeoMessage-PC-Windows.zip subido exitosamente.")
        except urllib.error.HTTPError as e:
            if e.code == 422:
                print("[INFO] NeoMessage-PC-Windows.zip ya estaba en GitHub.")
            else:
                print(f"[WARN] Error subiendo PC: {e.code}")

    # 4. Actualizar Firebase
    print("[*] Notificando a Firebase para actualizar todos los celulares...")
    fb_data = {
        'versionCode': version_code,
        'versionName': version_name,
        'apkUrl': f'https://github.com/{owner}/{repo}/releases/download/{tag_name}/{apk_file}',
        'pcUrl': f'https://github.com/{owner}/{repo}/releases/download/{tag_name}/NeoMessage-PC-Windows.zip',
        'changelog': '• Bot de Ventas 100% automático en segundo plano para clientes\n• Neo AI: Investigaciones completas y exhaustivas estructuradas\n• Recetario gastronómico inteligente con ingredientes y preparación paso a paso\n• Rendimiento ultra-fluido y llamadas HD',
        'isMandatory': False,
        'timestamp': 1793402000000
    }
    req_fb = urllib.request.Request(
        'https://neomessage-19ab4-default-rtdb.firebaseio.com/app_updates.json',
        data=json.dumps(fb_data).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='PUT'
    )
    with urllib.request.urlopen(req_fb) as resp_fb:
        print(f"[OK] Firebase actualizado con exito (HTTP {resp_fb.getcode()}).")

    print("\n=== DESPLIEGUE AUTOMATICO COMPLETADO AL 100% ===")
    print(f"Celular: https://github.com/{owner}/{repo}/releases/download/{tag_name}/{apk_file}")
    print(f"PC: https://github.com/{owner}/{repo}/releases/download/{tag_name}/NeoMessage-PC-Windows.zip")

if __name__ == '__main__':
    main()
