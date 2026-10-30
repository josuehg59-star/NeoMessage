import re

with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Pattern for showVaultPinDialog
pattern1 = r'\n\s*if\s*\(showVaultPinDialog\)\s*\{[\s\S]*?Text\("CANCELAR", color = Color\.Gray\)\s*\}\s*\}\s*\)\s*\}'
matches1 = re.findall(pattern1, text)
print(f'Matches pattern1: {len(matches1)}')
cleaned = re.sub(pattern1, '', text)

# Pattern for showSetPinDialog
pattern2 = r'\n\s*if\s*\(showSetPinDialog\)\s*\{[\s\S]*?Text\("CANCELAR", color = Color\.Gray\)\s*\}\s*\}\s*\)\s*\}'
matches2 = re.findall(pattern2, text)
print(f'Matches pattern2: {len(matches2)}')
cleaned = re.sub(pattern2, '', cleaned)

with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'w', encoding='utf-8') as f:
    f.write(cleaned)

print('Cleaned line count:', len(cleaned.splitlines()))
