with open('app/src/main/java/com/josuedev/neomessage/CommunitiesScreen.kt', 'r', encoding='utf-8') as f:
    comm_code = f.read()

# 1. Update CommunitiesScreen parameters
old_comm_head = """fun CommunitiesScreen(
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit
) {"""

new_comm_head = """fun CommunitiesScreen(
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    textSubColor: Color,
    accentGradient: Brush,
    onBack: () -> Unit,
    onNavigateToChat: (String, String) -> Unit = { _, _ -> },
    onNavigateToCall: (String, String) -> Unit = { _, _ -> }
) {"""

if old_comm_head in comm_code:
    comm_code = comm_code.replace(old_comm_head, new_comm_head)
    print("Updated CommunitiesScreen signature")

# 2. Update when (selectedTab)
old_when = """            when (selectedTab) {
                0 -> GroupsTab(groups, accentGradient, textColor, textSubColor, bgColor,
                    onCreateGroup = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateGroup = true },
                    onDeleteGroup = { id -> db.getReference("groups").child(id).removeValue() }
                )
                1 -> CommunitiesTab(communities, accentGradient, textColor, textSubColor, bgColor,
                    onCreateCommunity = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateCommunity = true },
                    onDeleteCommunity = { id -> db.getReference("groups").child(id).removeValue() }
                )
                2 -> BroadcastTab(broadcasts, accentGradient, textColor, textSubColor, bgColor,
                    onCreateBroadcast = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateBroadcast = true },
                    onDeleteBroadcast = { id -> db.getReference("groups").child(id).removeValue() }
                )"""

new_when = """            when (selectedTab) {
                0 -> GroupsTab(groups, accentGradient, textColor, textSubColor, bgColor,
                    onCreateGroup = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateGroup = true },
                    onDeleteGroup = { id -> db.getReference("groups").child(id).removeValue() },
                    onOpenGroup = { g -> onNavigateToChat(g.name, "group_${g.id}") },
                    onCallGroup = { g -> onNavigateToCall(g.name, "group_${g.id}") }
                )
                1 -> CommunitiesTab(communities, accentGradient, textColor, textSubColor, bgColor,
                    onCreateCommunity = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateCommunity = true },
                    onDeleteCommunity = { id -> db.getReference("groups").child(id).removeValue() },
                    onOpenCommunity = { g -> onNavigateToChat(g.name, "comm_${g.id}") },
                    onCallCommunity = { g -> onNavigateToCall(g.name, "comm_${g.id}") }
                )
                2 -> BroadcastTab(broadcasts, accentGradient, textColor, textSubColor, bgColor,
                    onCreateBroadcast = { createName = ""; createDesc = ""; selectedMembers = emptySet(); showCreateBroadcast = true },
                    onDeleteBroadcast = { id -> db.getReference("groups").child(id).removeValue() },
                    onOpenBroadcast = { g -> onNavigateToChat(g.name, "broad_${g.id}") }
                )"""

if old_when in comm_code:
    comm_code = comm_code.replace(old_when, new_when)
    print("Updated when(selectedTab)")

# 3. Update GroupsTab, CommunitiesTab, BroadcastTab
old_tabs_def = """// ==================== GRUPOS TAB ====================
@Composable
fun GroupsTab(
    groups: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateGroup: () -> Unit, onDeleteGroup: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (groups.isEmpty()) {
            item { EmptyState("Crea tu primer Grupo", "Habla simultáneamente con varios contactos.", Icons.Filled.Group, onCreateGroup) }
        } else {
            items(groups) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "group") { onDeleteGroup(g.id) }
            }
        }
        item {
            Button(onClick = onCreateGroup, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))) {
                Icon(Icons.Filled.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("NUEVO GRUPO", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

// ==================== COMUNIDADES TAB ====================
@Composable
fun CommunitiesTab(
    communities: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateCommunity: () -> Unit, onDeleteCommunity: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (communities.isEmpty()) {
            item { EmptyState("Crea una Comunidad", "Organiza múltiples grupos bajo una sola comunidad.", Icons.Filled.Groups, onCreateCommunity) }
        } else {
            items(communities) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "community") { onDeleteCommunity(g.id) }
            }
        }
        item {
            Button(onClick = onCreateCommunity, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                Icon(Icons.Filled.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("NUEVA COMUNIDAD", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

// ==================== DIFUSIÓN TAB ====================
@Composable
fun BroadcastTab(
    broadcasts: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateBroadcast: () -> Unit, onDeleteBroadcast: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        item {
            Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Los mensajes de difusión llegan individualmente. Solo ven que lo enviaste tú.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        if (broadcasts.isEmpty()) {
            item { EmptyState("Sin listas de difusión", "Envía mensajes a múltiples contactos a la vez.", Icons.Filled.Campaign, onCreateBroadcast) }
        } else {
            items(broadcasts) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "broadcast") { onDeleteBroadcast(g.id) }
            }
        }"""

new_tabs_def = """// ==================== GRUPOS TAB ====================
@Composable
fun GroupsTab(
    groups: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateGroup: () -> Unit, onDeleteGroup: (String) -> Unit,
    onOpenGroup: (NeoGroup) -> Unit = {},
    onCallGroup: (NeoGroup) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (groups.isEmpty()) {
            item { EmptyState("Crea tu primer Grupo", "Habla simultáneamente con varios contactos.", Icons.Filled.Group, onCreateGroup) }
        } else {
            items(groups) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "group",
                    onOpen = { onOpenGroup(g) },
                    onCall = { onCallGroup(g) },
                    onDelete = { onDeleteGroup(g.id) }
                )
            }
        }
        item {
            Button(onClick = onCreateGroup, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF7F))) {
                Icon(Icons.Filled.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("NUEVO GRUPO", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

// ==================== COMUNIDADES TAB ====================
@Composable
fun CommunitiesTab(
    communities: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateCommunity: () -> Unit, onDeleteCommunity: (String) -> Unit,
    onOpenCommunity: (NeoGroup) -> Unit = {},
    onCallCommunity: (NeoGroup) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (communities.isEmpty()) {
            item { EmptyState("Crea una Comunidad", "Organiza múltiples grupos bajo una sola comunidad.", Icons.Filled.Groups, onCreateCommunity) }
        } else {
            items(communities) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "community",
                    onOpen = { onOpenCommunity(g) },
                    onCall = { onCallCommunity(g) },
                    onDelete = { onDeleteCommunity(g.id) }
                )
            }
        }
        item {
            Button(onClick = onCreateCommunity, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) {
                Icon(Icons.Filled.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("NUEVA COMUNIDAD", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

// ==================== DIFUSIÓN TAB ====================
@Composable
fun BroadcastTab(
    broadcasts: List<NeoGroup>, accentGradient: Brush,
    textColor: Color, textSubColor: Color, bgColor: Color,
    onCreateBroadcast: () -> Unit, onDeleteBroadcast: (String) -> Unit,
    onOpenBroadcast: (NeoGroup) -> Unit = {}
) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(bgColor)) {
        item {
            Surface(color = Color(0xFF1A1A2E), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Los mensajes de difusión llegan individualmente. Solo ven que lo enviaste tú.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
        if (broadcasts.isEmpty()) {
            item { EmptyState("Sin listas de difusión", "Envía mensajes a múltiples contactos a la vez.", Icons.Filled.Campaign, onCreateBroadcast) }
        } else {
            items(broadcasts) { g ->
                GroupItem(g, accentGradient, textColor, textSubColor, "broadcast",
                    onOpen = { onOpenBroadcast(g) },
                    onDelete = { onDeleteBroadcast(g.id) }
                )
            }
        }"""

if old_tabs_def in comm_code:
    comm_code = comm_code.replace(old_tabs_def, new_tabs_def)
    print("Updated tabs definitions")

# 4. Update GroupItem composable
old_group_item = """@Composable
fun GroupItem(g: NeoGroup, grad: Brush, textColor: Color, textSubColor: Color, type: String, onDelete: () -> Unit) {
    val typeLabel = when (type) { "community" -> "Comunidad"; "broadcast" -> "Difusión"; else -> "Grupo" }
    val typeColor = when (type) { "community" -> Color(0xFFFF9800); "broadcast" -> Color(0xFFE91E63); else -> Color(0xFF00FF7F) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(grad), contentAlignment = Alignment.Center) {
            Text(g.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Surface(color = typeColor.copy(0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(typeLabel, color = typeColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
            Text("${g.members.size} participante(s)${if (g.description.isNotEmpty()) " · ${g.description}" else ""}", color = textSubColor, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, null, tint = Color.Gray.copy(0.6f), modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = Color.White.copy(0.05f))
}"""

new_group_item = """@Composable
fun GroupItem(
    g: NeoGroup, 
    grad: Brush, 
    textColor: Color, 
    textSubColor: Color, 
    type: String, 
    onOpen: () -> Unit = {},
    onCall: () -> Unit = {},
    onDelete: () -> Unit
) {
    val typeLabel = when (type) { "community" -> "Comunidad"; "broadcast" -> "Difusión"; else -> "Grupo" }
    val typeColor = when (type) { "community" -> Color(0xFFFF9800); "broadcast" -> Color(0xFFE91E63); else -> Color(0xFF00FF7F) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(grad), contentAlignment = Alignment.Center) {
            Text(g.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Surface(color = typeColor.copy(0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(typeLabel, color = typeColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
            Text("${g.members.size} participante(s)${if (g.description.isNotEmpty()) " · ${g.description}" else ""}", color = textSubColor, fontSize = 12.sp, maxLines = 1)
        }
        if (type != "broadcast") {
            IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Call, contentDescription = "Llamada grupal", tint = Color(0xFF00FF7F), modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, null, tint = Color.Gray.copy(0.6f), modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = Color.White.copy(0.05f))
}"""

if old_group_item in comm_code:
    comm_code = comm_code.replace(old_group_item, new_group_item)
    print("Updated GroupItem")

with open('app/src/main/java/com/josuedev/neomessage/CommunitiesScreen.kt', 'w', encoding='utf-8') as f:
    f.write(comm_code)
print("CommunitiesScreen.kt saved.")
