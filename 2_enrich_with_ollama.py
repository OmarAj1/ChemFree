import sqlite3
import json
import asyncio
import aiohttp

DB_FILE = "MasterUnifiedDB.db" # The merged DB we just created
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "llama3.2"  
MAX_CONCURRENT_REQUESTS = 10  

def fetch_pending_ingredients():
    conn = sqlite3.connect(DB_FILE, timeout=60.0)
    cursor = conn.cursor()
    
    # Pull only items that haven't been enriched yet. The merge script guaranteed a single 'name' column.
    query = """
        SELECT id, name
        FROM UnifiedIngredients 
        WHERE is_enriched = 0 
    """
    try:
        cursor.execute(query)
        rows = cursor.fetchall()
    except Exception as e:
        print(f"   [!] Error selecting pending rows: {e}")
        rows = []
        
    conn.close()
    return rows

def save_enriched_results(results):
    if not results: return
    print(f"\nSaving batch of {len(results)} processed ingredients...")
    conn = sqlite3.connect(DB_FILE, timeout=60.0)
    cursor = conn.cursor()
    try:
        for item in results:
            plain_english, purpose, risks, risk_level, dietary, row_id = item
            cursor.execute('''
                UPDATE UnifiedIngredients 
                SET plain_english_name = ?, purpose = ?, health_risks = ?, risk_level = ?, dietary_safety = ?, is_enriched = 1
                WHERE id = ?
            ''', (plain_english, purpose, risks, risk_level, dietary, row_id))
        conn.commit()
    except Exception as e:
        print(f"Error saving batch: {e}")
    finally:
        conn.close()

async def ask_ollama_async(session, ingredient_name, max_retries=3):
    prompt = f"""
Analyze the following food ingredient and output the result in RAW JSON format.
Do not wrap the response in markdown blocks.

Ingredient Name: "{ingredient_name}"

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

async def process_ingredient(semaphore, session, row_id, name, total, idx, shared_results_list):
    async with semaphore:
        print(f"Processing {idx}/{total}: {name}")
        result_json = await ask_ollama_async(session, name)
        
        if result_json:
            plain_english = result_json.get("plain_english_name", "")
            purpose = result_json.get("purpose", "")
            risks = result_json.get("health_risks", "")
            risk_lvl = result_json.get("risk_level", "Unknown")
            dietary = result_json.get("dietary_safety", "")
            
            shared_results_list.append((plain_english, purpose, risks, risk_lvl, dietary, row_id))
            print(f"  -> Processed: {plain_english} [Risk: {risk_lvl}]")
        else:
            print(f"  -> Skipped due to processing errors.")

async def enrich_data_async():
    rows = fetch_pending_ingredients()
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
            row_id, name = row
            task = asyncio.create_task(
                process_ingredient(semaphore, session, row_id, name, total_pending, idx, shared_results)
            )
            tasks.append(task)
            
            if len(tasks) >= 50 or idx == total_pending:
                await asyncio.gather(*tasks)
                tasks = []
                save_enriched_results(shared_results)
                shared_results.clear()

if __name__ == "__main__":
    print("\nStarting Ultra-Fast Ollama Enrichment Phase. Press Ctrl+C to pause.")
    try:
        asyncio.run(enrich_data_async())
    except KeyboardInterrupt:
        print("\nEnrichment paused safely.")
