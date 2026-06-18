import sqlite3
import glob
import json
import asyncio
import aiohttp

MASTER_DB = "MasterUnifiedDB.db"
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "llama3.2"  

# These are the perfect clean columns we want our final database to have
MASTER_COLUMNS = [
    "name",
    "description",
    "identification_code",
    "toxicity_or_safety",
    "category",
    "dietary_info"
]

def get_table_columns(cursor, table_name):
    cursor.execute(f"PRAGMA table_info('{table_name}')")
    return [row[1] for row in cursor.fetchall()]

async def ask_ollama_for_schema_mapping(session, db_file, table_name, columns):
    prompt = f"""
I am merging multiple unstructured food ingredient databases into one clean database. 
I have a table named '{table_name}' from database '{db_file}' with the following columns:
{columns}

Map these messy columns to my master schema.
Master Schema: {MASTER_COLUMNS}

Rules:
1. Pick the best matching column from the table for each master column. Use your intelligence to figure out what columns like '2131234_id' or 'desc_v2' mean.
2. If there is no matching column in the table for a master column, use null.
3. "name" MUST be populated if there is any column that represents the substance/food name.

Respond strictly in RAW JSON format mapping master columns to the table's column names. 
Example Output:
{{
  "name": "messy_name_col",
  "description": "desc_v2",
  "identification_code": "2131234_id",
  "toxicity_or_safety": null,
  "category": "FoodCategory",
  "dietary_info": null
}}
"""
    payload = {"model": MODEL_NAME, "prompt": prompt, "stream": False, "format": "json"}
    
    try:
        async with session.post(OLLAMA_URL, json=payload, timeout=60) as response:
            if response.status == 200:
                res_json = await response.json()
                return json.loads(res_json.get("response", "{}"))
    except Exception as e:
        print(f"Error mapping {table_name}: {e}")
    return None

async def map_all_schemas():
    db_files = set(glob.glob("*.db") + glob.glob("*.sqlite"))
    if MASTER_DB in db_files: db_files.remove(MASTER_DB)
    
    table_mappings = []
    
    async with aiohttp.ClientSession() as session:
        for db_file in db_files:
            conn = sqlite3.connect(db_file)
            cursor = conn.cursor()
            cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT IN ('sqlite_sequence', 'UltimateIngredients', 'EnrichedIngredients');")
            tables = [row[0] for row in cursor.fetchall()]
            
            for table in tables:
                cols = get_table_columns(cursor, table)
                if not cols: continue
                
                print(f"\n[AI Mapping] Analyzing: {db_file} -> {table}")
                mapping = await ask_ollama_for_schema_mapping(session, db_file, table, cols)
                if mapping:
                    table_mappings.append((db_file, table, mapping, cols))
                    print(f"  ✓ AI Decision: {mapping}")
                else:
                    print(f"  x AI failed to map {table}.")
            conn.close()
            
    return table_mappings

def execute_ai_merge(table_mappings):
    print("\n--- Phase 2: Extracting and Merging Data using AI Mapping ---")
    grouped_data = {}
    
    for db_file, table, mapping, cols in table_mappings:
        try:
            conn = sqlite3.connect(db_file)
            cursor = conn.cursor()
            cursor.execute(f"SELECT * FROM '{table}'")
            rows = cursor.fetchall()
            
            # Map index position safely
            col_list = [c.lower() for c in cols]
            mapped_indices = {}
            for m_key, m_col in mapping.items():
                if m_col:
                    m_col_lower = str(m_col).lower()
                    if m_col_lower in col_list:
                        mapped_indices[m_key] = col_list.index(m_col_lower)
            
            name_idx = mapped_indices.get("name")
            if name_idx is None:
                print(f"Skipping {table}: AI could not identify a valid Name column.")
                continue 
                
            for row in rows:
                raw_name = row[name_idx]
                if not raw_name or str(raw_name).strip() == "": 
                    continue
                
                norm_name = str(raw_name).strip().lower()
                
                if norm_name not in grouped_data:
                    grouped_data[norm_name] = {"name": str(raw_name).strip()}
                    
                # Merge data columns together, picking the most descriptive text
                for m_key, idx in mapped_indices.items():
                    if m_key == "name": continue
                    
                    val = row[idx]
                    if val and str(val).strip():
                        existing_v = grouped_data[norm_name].get(m_key)
                        # Conflict logic: Keep the longest descriptive value for descriptions/safety info
                        if not existing_v or len(str(val)) > len(str(existing_v)):
                            grouped_data[norm_name][m_key] = val
                            
            conn.close()
        except Exception as e:
            print(f"Failed extracting from {table}: {e}")

    # Save logic
    print(f"\n--- Phase 3: Saving {len(grouped_data)} deeply merged items ---")
    out_conn = sqlite3.connect(MASTER_DB)
    out_cursor = out_conn.cursor()
    
    out_cursor.execute("DROP TABLE IF EXISTS UnifiedIngredients")
    
    cols_def = ["[id] INTEGER PRIMARY KEY AUTOINCREMENT"]
    for mc in MASTER_COLUMNS:
        cols_def.append(f"[{mc}] TEXT")
        
    out_cursor.execute(f"CREATE TABLE UnifiedIngredients ({', '.join(cols_def)}, plain_english_name TEXT, purpose TEXT, health_risks TEXT, risk_level TEXT, dietary_safety TEXT, is_enriched INTEGER DEFAULT 0);")
    
    insert_sql = f"INSERT INTO UnifiedIngredients ({', '.join([f'[{mc}]' for mc in MASTER_COLUMNS])}) VALUES ({', '.join(['?']*len(MASTER_COLUMNS))})"
    
    batch = []
    for data in grouped_data.values():
        row_vals = [data.get(mc) for mc in MASTER_COLUMNS]
        batch.append(row_vals)
        
    out_cursor.executemany(insert_sql, batch)
    out_conn.commit()
    out_conn.close()
    
    print(f"\nSuccess! Master database created at {MASTER_DB} using AI Column Mapping.")

if __name__ == "__main__":
    print("Starting AI Database Column Schema Mapper...")
    mappings = asyncio.run(map_all_schemas())
    execute_ai_merge(mappings)
