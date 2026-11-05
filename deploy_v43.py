import os, sys, shutil, subprocess, time, json, urllib.request, re

REPO = "josuehg59-star/NeoMessage"
VERSION_TAG = "v3.43"
VERSION_NAME = "NeoMessage v3.43 - Sincronización Soberana Celular-PC + Enlaces TikTok/YouTube + Estados Multimedia"

proc = subprocess.run(["git", "config", "remote.origin.url"], capture_output=True, text=True)
m = re.search(r':(ghp_[^@]+)@', proc.stdout)
token = m.group(1) if m else None

apk_source = r"app\build\outputs\apk\release\app-release.apk"
if not os.path.exists(apk_source):
    print("APK not found at", apk_source)
    sys.exit(1)

desktop = os.path.join(os.environ["USERPROFILE"], "Desktop")
shutil.copy2(apk_source, os.path.join(desktop, "NeoMessage-v3.43.apk"))
shutil.copy2(apk_source, os.path.join(desktop, "NeoMessage.apk"))
shutil.copy2(apk_source, "NeoMessage.apk")
print("Copied APK to Desktop and root!")

headers = {"User-Agent": "NeoMessage-Deployer", "Accept": "application/vnd.github.v3+json"}
if token:
    headers["Authorization"] = f"token {token}"

rel_url = f"https://api.github.com/repos/{REPO}/releases/tags/{VERSION_TAG}"
req = urllib.request.Request(rel_url, headers=headers)
try:
    with urllib.request.urlopen(req) as resp:
        rdata = json.loads(resp.read().decode("utf-8"))
        upload_url = rdata["upload_url"].split("{")[0]
        print("Release already exists, upload_url found")
except urllib.error.HTTPError as e:
    if e.code == 404:
        create_url = f"https://api.github.com/repos/{REPO}/releases"
        payload = json.dumps({
            "tag_name": VERSION_TAG,
            "target_commitish": "main",
            "name": VERSION_NAME,
            "body": "Versión 3.43 Soberana:\\n- Sincronización total Celular ⇄ PC con almacenamiento local indestructible (SQLite Room en cel + IndexedDB en PC).\\n- Modo descanso protegido: los mensajes esperan pacientemente en Firebase antes de liberarse tras confirmación de guardado físico.\\n- Enlaces interactivos estilo WhatsApp: abre videos de TikTok, YouTube Shorts y sitios web con un solo toque.\\n- Orden dinámico en bandeja: las conversaciones activas saltan automáticamente al primer lugar con mensajes no leídos en negrita.\\n- Reenvío interno de fotos, videos y audios sin menús externos.\\n- Estados multimedia con video y música real de fondo.\\n- Nueva versión para PC Windows actualizada con sincronización IndexedDB.",
            "draft": False,
            "prerelease": False
        }).encode("utf-8")
        creq = urllib.request.Request(create_url, data=payload, headers=headers)
        with urllib.request.urlopen(creq) as cresp:
            crdata = json.loads(cresp.read().decode("utf-8"))
            upload_url = crdata["upload_url"].split("{")[0]
            print("Release created successfully:", VERSION_TAG)
    else:
        raise

with open(apk_source, "rb") as f:
    apk_data = f.read()

up_req = urllib.request.Request(f"{upload_url}?name=NeoMessage.apk", data=apk_data, headers={
    "Authorization": f"token {token}",
    "Content-Type": "application/vnd.android.package-archive",
    "User-Agent": "NeoMessage-Deployer"
})
try:
    with urllib.request.urlopen(up_req) as uresp:
        udata = json.loads(uresp.read().decode("utf-8"))
        print("Uploaded APK asset:", udata.get("browser_download_url"))
except Exception as e:
    print("Upload APK warning (may exist):", e)

# Upload PC ZIP asset
zip_source = r"pc-distribution\NeoMessage-PC-Windows.zip"
if os.path.exists(zip_source):
    with open(zip_source, "rb") as f:
        zip_data = f.read()
    up_zip_req = urllib.request.Request(f"{upload_url}?name=NeoMessage-PC-Windows.zip", data=zip_data, headers={
        "Authorization": f"token {token}",
        "Content-Type": "application/zip",
        "User-Agent": "NeoMessage-Deployer"
    })
    try:
        with urllib.request.urlopen(up_zip_req) as zresp:
            zdata = json.loads(zresp.read().decode("utf-8"))
            print("Uploaded PC ZIP asset:", zdata.get("browser_download_url"))
    except Exception as e:
        print("Upload PC ZIP warning (may exist):", e)

for tag in ["v3.42", "v3.41", "v3.40", "v3.39"]:
    try:
        t_url = f"https://api.github.com/repos/{REPO}/releases/tags/{tag}"
        with urllib.request.urlopen(urllib.request.Request(t_url, headers=headers)) as tresp:
            tdata = json.loads(tresp.read().decode("utf-8"))
            for asset in tdata.get("assets", []):
                if asset["name"] == "NeoMessage.apk":
                    urllib.request.urlopen(urllib.request.Request(asset["url"], headers=headers, method="DELETE"))
            t_up = tdata["upload_url"].split("{")[0] + "?name=NeoMessage.apk"
            urllib.request.urlopen(urllib.request.Request(t_up, data=apk_data, headers={
                "Authorization": f"token {token}",
                "Content-Type": "application/vnd.android.package-archive",
                "User-Agent": "NeoMessage-Deployer"
            }))
            print(f"Updated NeoMessage.apk in {tag}")
    except Exception as e:
        print(f"Notice {tag}:", e)

fb_url = "https://neomessage-19ab4-default-rtdb.firebaseio.com/app_updates.json"
primary_apk_url = f"https://github.com/{REPO}/releases/download/{VERSION_TAG}/NeoMessage.apk"
primary_pc_url = f"https://github.com/{REPO}/releases/download/{VERSION_TAG}/NeoMessage-PC-Windows.zip"
update_payload = {
    "versionCode": 53,
    "versionName": "3.43-SOVEREIGN",
    "apkUrl": primary_apk_url,
    "downloadUrl": primary_apk_url,
    "fallbackUrl": f"https://github.com/{REPO}/releases/latest/download/NeoMessage.apk",
    "pcUrl": primary_pc_url,
    "isMandatory": True,
    "changelog": "v3.43: Sincronización Celular-PC permanente, enlaces interactivos de TikTok/YouTube estilo WhatsApp, orden prioritario de chats y estados multimedia con música/video.",
    "changeLog": "v3.43: Sincronización Celular-PC permanente, enlaces interactivos de TikTok/YouTube estilo WhatsApp, orden prioritario de chats y estados multimedia con música/video.",
    "releaseDate": "04 Noviembre 2026",
    "timestamp": int(time.time() * 1000)
}
fbreq = urllib.request.Request(fb_url, data=json.dumps(update_payload).encode("utf-8"), headers={"Content-Type": "application/json"}, method="PUT")
with urllib.request.urlopen(fbreq) as resp:
    print("Firebase app_updates updated to v3.43! Status:", resp.status)

print("DEPLOY v3.43 COMPLETED SUCCESSFULLY!")
