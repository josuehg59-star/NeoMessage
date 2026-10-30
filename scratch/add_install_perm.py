with open('app/src/main/AndroidManifest.xml', 'r', encoding='utf-8') as f:
    code = f.read()

target = '<uses-permission android:name="android.permission.INTERNET" />'
replacement = '<uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />'

if target in code and 'REQUEST_INSTALL_PACKAGES' not in code:
    code = code.replace(target, replacement)
    with open('app/src/main/AndroidManifest.xml', 'w', encoding='utf-8') as f:
        f.write(code)
    print("REQUEST_INSTALL_PACKAGES added successfully")
else:
    print("Already present")
