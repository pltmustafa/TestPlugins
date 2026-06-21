import os
import re

base_dir = '/Users/mustafapolat/Desktop/Cloudstream'
updated_files = 0

for root, dirs, files in os.walk(base_dir):
    for f in files:
        if f.endswith('.kt') and 'build' not in root and 'Cloud-Sync' not in root:
            filepath = os.path.join(root, f)
            with open(filepath, 'r') as file:
                content = file.read()
            
            # Find the loadLinks function body
            # Matches: override suspend fun loadLinks( ... ): Boolean { ... }
            pattern = r'(override\s+suspend\s+fun\s+loadLinks\s*\([\s\S]*?\)\s*:\s*Boolean\s*\{)'
            match = re.search(pattern, content)
            
            if not match:
                continue
                
            start_index = match.end()
            
            # Extract the body of loadLinks by counting braces
            brace_count = 1
            end_index = start_index
            for i in range(start_index, len(content)):
                if content[i] == '{':
                    brace_count += 1
                elif content[i] == '}':
                    brace_count -= 1
                    
                if brace_count == 0:
                    end_index = i
                    break
                    
            load_links_body = content[start_index:end_index]
            
            # If "return false" is found, replace it with throw Exception
            if 'return false' in load_links_body:
                # Use regex to replace "return false" ensuring word boundaries
                new_body = re.sub(r'\breturn\s+false\b', 'throw Exception("Gerekli veri bulunamadı")', load_links_body)
                
                if new_body != load_links_body:
                    new_content = content[:start_index] + new_body + content[end_index:]
                    with open(filepath, 'w') as file:
                        file.write(new_content)
                    print(f"Updated {filepath}")
                    updated_files += 1

print(f"Successfully updated {updated_files} files.")
