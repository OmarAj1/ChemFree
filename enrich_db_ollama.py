import sqlite3
import json
import asyncio
import aiohttp

DB_FILE = "MergedFoodDB_Fixed.db"
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "llama3.2"  
MAX_CONCURRENT_REQUESTS = 10  

def get_table_columns(cursor, table_name):
    """Dynamically get all column names and types from a table."""
    try:
        cursor.execute(f"PRAGMA table_info({table_name})")
        return {row[1]: row[2] for row in cursor.fetchall()} # {column_name: data_type}
    except Exception:
        return {}

def create_ultimate_table():
    """Dynamically scans all source tables and builds a master table with all columns."""
    print("Analyzing source tables to build the ultimate master table...")
    conn = sqlite3.connect(DB_FILE, timeout=60.0)
    cursor = conn.cursor()
    
    # 1. Gather all tables dynamically from the database
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT IN ('sqlite_sequence', 'UltimateIngredients', 'EnrichedIngredients');")
    tables = [row[0] for row in cursor.fetchall()]
    print(f"Found {len(tables)} tables to merge: {', '.join(tables)}")
    
    all_columns = {} # Master dictionary of {column_name: type}
    
    for table in tables:
        cols = get_table_columns(cursor, table)
        for col_name, col_type in cols.items():
            # Standardize column names to lowercase to merge identical ones like 'Name' and 'name'
            norm_name = col_name.strip().lower()
            if norm_name not in all_columns:
                all_columns[norm_name] = col_type if col_type else "TEXT"

    if not all_columns:
        print("[!] Error: No columns found. Make sure the source tables exist in the database.")
        conn.close()
        return

    # 2. Add our AI enrichment columns to the master list definition
    enrich_cols = {
        "id": "INTEGER PRIMARY KEY AUTOINCREMENT",
        "plain_english_name": "TEXT",
        "purpose": "TEXT",
        "health_risks": "TEXT",
        "risk_level": "TEXT",
        "dietary_safety": "TEXT",
        "is_enriched": "INTEGER DEFAULT 0",
        "source_table_origin": "TEXT"
    }
    
    # Clean up standard name collisions if they exist in source tables
    for k, v in enrich_cols.items():
        all_columns[k] = v

    # 3. Construct the SQL to create UltimateIngredients table
    col_definitions = [f"[{col}] {col_type}" for col, col_type in all_columns.items() if col != "id"]
    col_definitions.insert(0, "[id] INTEGER PRIMARY KEY AUTOINCREMENT") # Ensure ID is first
    
    create_sql = f"CREATE TABLE IF NOT EXISTS UltimateIngredients ({', '.join(col_definitions)});"
    cursor.execute(create_sql)
    
    # Ensure risk_level is added to existing tables
    try:
        cursor.execute("ALTER TABLE UltimateIngredients ADD COLUMN risk_level TEXT;")
    except sqlite3.OperationalError:
        pass
        
    conn.commit()
    print("-> UltimateIngredients table successfully created with all columns.")
    
    # 4. Migrate raw data into the Ultimate table without filtering anything
    print("Migrating raw data from all tables...")
    for table in tables:
        src_cols = get_table_columns(cursor, table)
        if not src_cols:
            continue
            
        src_col_names = list(src_cols.keys())
        select_fields = ", ".join([f"[{c}]" for c in src_col_names])
        
        # Map target columns (normalized lowercase names)
        target_fields = ", ".join([f"[{c.lower()}]" for c in src_col_names]) + ", [source_table_origin]"
        
        try:
            cursor.execute(f"SELECT {select_fields} FROM {table}")
            rows = cursor.fetchall()
            
            # Insert every row directly
            for row in rows:
                placeholders = ", ".join(["?"] * (len(row) + 1))
                insert_sql = f"INSERT INTO UltimateIngredients ({target_fields}) VALUES ({placeholders})"
                cursor.execute(insert_sql, row + (table,))
        except Exception as e:
            print(f"   Error migrating {table}: {e}")

    conn.commit()
    conn.close()
    print("Migration finished. All records are unified in UltimateIngredients.")

def fetch_deduplicated_pending():
    """Identifies unique items based on name columns to feed into Ollama."""
    conn = sqlite3.connect(DB_FILE, timeout=60.0)
    cursor = conn.cursor()
    
    cursor.execute("PRAGMA table_info(UltimateIngredients)")
    all_cols = [row[1] for row in cursor.fetchall()]
    
    # Gather all possible name columns that exist in the ultimate table
    possible_names = ["name", "chemical_name", "title", "compound_name", "food_name", "ingredient_name", "flavor_name"]
    name_cols_present = [c for c in possible_names if c in all_cols]
    
    if not name_cols_present:
        print("[!] Warning: Could not find any standard name columns!")
        return [], []
        
    # COALESCE returns the first non-null value among the columns
    coalesce_expr = f"COALESCE({', '.join(f'[{c}]' for c in name_cols_present)})"
        
    # Pick up alternate code tracks safely
    possible_codes = ["e_no", "fl_no", "cas", "accessionnumber", "id"]
    code_col = next((c for c in possible_codes if c in all_cols), "id")

    # Select distinct values to process to prevent wasting time on duplicates
    query = f"""
        SELECT MIN(id), {coalesce_expr}, [{code_col}] 
        FROM UltimateIngredients 
        WHERE is_enriched = 0 
          AND {coalesce_expr} IS NOT NULL 
          AND TRIM({coalesce_expr}) != ''
        GROUP BY LOWER({coalesce_expr})
    """
    try:
        cursor.execute(query)
        rows = cursor.fetchall()
    except Exception as e:
        print(f"   [!] Error selecting pending rows: {e}")
        rows = []
        
    conn.close()
    return rows, name_cols_present

def save_enriched_results(results, name_cols_present):
    """Updates all duplicate instances matching the cleaned ingredient name in bulk."""
    if not results: return
    print(f"\nSaving batch of {len(results)} processed ingredients...")
    conn = sqlite3.connect(DB_FILE, timeout=60.0)
    cursor = conn.cursor()
    try:
        update_clauses = " OR ".join([f"LOWER([{c}]) = LOWER(?)" for c in name_cols_present])
        
        for item in results:
            plain_english, purpose, risks, risk_level, dietary, orig_name = item
            
            # We need to supply the orig_name parameter once for each OR condition
            params = [plain_english, purpose, risks, risk_level, dietary] + [orig_name] * len(name_cols_present)
            
            # Update EVERY row that shares this lowercase name across any generic name column
            cursor.execute(f'''
                UPDATE UltimateIngredients 
                SET plain_english_name = ?, purpose = ?, health_risks = ?, risk_level = ?, dietary_safety = ?, is_enriched = 1
                WHERE {update_clauses}
            ''', tuple(params))
        conn.commit()
        print("Batch save complete and database unlocked successfully.")
    except Exception as e:
        print(f"Error saving batch: {e}")
    finally:
        conn.close()

async def ask_ollama_async(session, ingredient_name, codes, max_retries=3):
    prompt = f"""
Analyze the following food ingredient and output the result in RAW JSON format.
Do not wrap the response in markdown blocks.

Ingredient Name: "{ingredient_name}"
Known industry codes: "{codes}"

Expected JSON Structure:
{{
  "plain_english_name": "What average people call this (short)",
  "purpose": "Why manufacturers use it",
  "health_risks": "Health risks or 'Generally recognized as safe'.",
  "risk_level": "Categorize risk exactly as: 'Low', 'Moderate', 'High', or 'Unknown'",
  "dietary_safety": "e.g., vegan, halal, gluten-free"
}}
"""
    payload = {"model": MODEL_NAME, "prompt": prompt, "stream": False, "format": "json"}
    timeout = aiohttp.ClientTimeout(total=90)
    
    for attempt in range(max_retries):
        try:
            async with session.post(OLLAMA_URL, json=payload, timeout=timeout) as response:
                if response.status == 200:
                    res_json = await response.json()
                    return json.loads(res_json.get("response", "{}"))
        except Exception:
            await asyncio.sleep(1)
    return None

async def process_ingredient(semaphore, session, row_id, name, codes, total, idx, shared_results_list):
    async with semaphore:
        print(f"Processing {idx}/{total}: {name}")
        result_json = await ask_ollama_async(session, name, codes)
        
        if result_json:
            plain_english = result_json.get("plain_english_name", "")
            purpose = result_json.get("purpose", "")
            risks = result_json.get("health_risks", "")
            risk_lvl = result_json.get("risk_level", "Unknown")
            dietary = result_json.get("dietary_safety", "")
            
            # Save using the raw string name so we can match and clear duplicates out completely
            shared_results_list.append((plain_english, purpose, risks, risk_lvl, dietary, name))
            print(f"  -> Processed: {plain_english} [Risk: {risk_lvl}]")
        else:
            print(f"  -> Skipped due to processing errors.")

async def enrich_data_async():
    rows, name_cols_present = fetch_deduplicated_pending()
    total_pending = len(rows)
    
    print(f"\nFound {total_pending} unique ingredients to process using Ollama ({MODEL_NAME}).")
    if total_pending == 0: return

    semaphore = asyncio.Semaphore(MAX_CONCURRENT_REQUESTS)
    shared_results = []
    
    print(f"Warming up {MODEL_NAME}...")
    async with aiohttp.ClientSession() as session:
        try:
            await session.post(OLLAMA_URL, json={"model": MODEL_NAME, "prompt": "ping", "stream": False}, timeout=15)
        except Exception: pass

        tasks = []
        for idx, row in enumerate(rows, start=1):
            row_id, name, codes = row
            task = asyncio.create_task(
                process_ingredient(semaphore, session, row_id, name, codes, total_pending, idx, shared_results)
            )
            tasks.append(task)
            
            if len(tasks) >= 50 or idx == total_pending:
                await asyncio.gather(*tasks)
                tasks = []
                save_enriched_results(shared_results, name_cols_present)
                shared_results.clear()

if __name__ == "__main__":
    create_ultimate_table()
    
    print("\nStarting Ultra-Fast Ollama Enrichment Phase. Press Ctrl+C to pause.")
    try:
        asyncio.run(enrich_data_async())
    except KeyboardInterrupt:
        print("\nEnrichment paused safely.")
