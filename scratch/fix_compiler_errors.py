# 1. Fix NeoCommerceManager.kt
with open('app/src/main/java/com/josuedev/neomessage/NeoCommerceManager.kt', 'r', encoding='utf-8') as f:
    ncm = f.read()

ncm = ncm.replace('MXN\\n  _$prods_\\n', 'MXN\\n  _${prods}_\\n')
with open('app/src/main/java/com/josuedev/neomessage/NeoCommerceManager.kt', 'w', encoding='utf-8') as f:
    f.write(ncm)
print("Fixed NeoCommerceManager.kt")

# 2. Fix NeoCommerceScreens.kt
with open('app/src/main/java/com/josuedev/neomessage/NeoCommerceScreens.kt', 'r', encoding='utf-8') as f:
    ncs = f.read()

ncs = ncs.replace('cartItems = emptyList()', 'cartItems = mutableListOf<CartItem>()')
with open('app/src/main/java/com/josuedev/neomessage/NeoCommerceScreens.kt', 'w', encoding='utf-8') as f:
    f.write(ncs)
print("Fixed NeoCommerceScreens.kt")

# 3. Fix ChatRoomScreen.kt
with open('app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt', 'r', encoding='utf-8') as f:
    crs = f.read()

# Add showCommerceSheet at top
old_state = 'var playingVoiceMsgId by remember { mutableStateOf<String?>(null) }'
new_state = 'var playingVoiceMsgId by remember { mutableStateOf<String?>(null) }\n    var showCommerceSheet by remember { mutableStateOf(false) }'

if old_state in crs:
    crs = crs.replace(old_state, new_state)
    print("Added showCommerceSheet state at top of ChatRoomScreen")

# Remove duplicate declaration in actions block
crs = crs.replace('var showCommerceSheet by remember { mutableStateOf(false) }\n                    IconButton(onClick = { showCommerceSheet = true })', 'IconButton(onClick = { showCommerceSheet = true })')

# Update onOrderPlaced call
old_order = 'onOrderPlaced = { receipt ->\n                sendMessage(receipt)\n            }'
new_order = 'onOrderPlaced = { receipt ->\n                messageText = receipt\n                sendMessage()\n            }'

if old_order in crs:
    crs = crs.replace(old_order, new_order)
    print("Updated onOrderPlaced to use messageText = receipt; sendMessage()")

with open('app/src/main/java/com/josuedev/neomessage/ChatRoomScreen.kt', 'w', encoding='utf-8') as f:
    f.write(crs)
print("Fixed ChatRoomScreen.kt")
