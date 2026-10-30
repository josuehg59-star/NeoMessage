with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Find the exact repeated block
start_marker = '        }\n\n        if (showSetPinDialog) {'
end_marker = 'Text("CANCELAR", color = Color.Gray) }\n                }\n            )\n        }'

# Let's inspect where it appears and find the exact repeated block
idx = content.find('        if (showSetPinDialog) {')
if idx != -1:
    end_idx = content.find('Text("CANCELAR", color = Color.Gray) }\n                }\n            )\n        }', idx)
    if end_idx != -1:
        end_idx += len('Text("CANCELAR", color = Color.Gray) }\n                }\n            )\n        }')
        repeated_block = content[idx:end_idx]
        print("Found repeated block, length:", len(repeated_block))
        content = content.replace('\n\n' + repeated_block, '')
        with open('app/src/main/java/com/josuedev/neomessage/MainComponents.kt', 'w', encoding='utf-8') as f:
            f.write(content)
        print("Cleaned up! Total lines now:", len(content.splitlines()))
    else:
        print("End marker not found")
else:
    print("Start marker not found")
