import os

path = r'app/src/main/java/com/josuedev/neomessage/Models.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if 'fun getFileSize' in line:
        pass
    if line.strip() == '} catch (ignored: Exception) { 0L }':
        pass

# Find the NeoUtils closing brace
utils_found = False
brace_count = 0
insert_idx = -1
for i, line in enumerate(lines):
    if 'object NeoUtils' in line:
        utils_found = True
    if utils_found:
        if '{' in line:
            brace_count += line.count('{')
        if '}' in line:
            brace_count -= line.count('}')
            if brace_count == 0:
                insert_idx = i
                break

if insert_idx != -1:
    fn_code = """
    fun getFileName(context: Context, uri: Uri): String {
        return try {
            var name = "Documento"
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && it.moveToFirst()) {
                    name = it.getString(nameIndex) ?: "Documento"
                }
            }
            name
        } catch (ignored: Exception) { "Documento" }
    }
"""
    lines.insert(insert_idx, fn_code)
    with open(path, 'w', encoding='utf-8') as f:
        f.writelines(lines)
    print("SUCCESS: Added getFileName before line", insert_idx)
else:
    print("ERROR: NeoUtils not found or closing brace not located")
