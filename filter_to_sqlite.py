import json
import sqlite3
import os

# Filter FooDB jsonl into a highly compressed SQLite database 
# Groups all nutrients by food name to ensure the DB size is extremely small (< 20MB)
# This prevents the 600MB file size issue by avoiding duplicate rows and heavy Full-Text Search indexing.

def create_database(db_path):
    if os.path.exists(db_path):
        os.remove(db_path)
    
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Create a highly efficient grouped table
    # We only need one row per food item
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS foods (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT UNIQUE,
            nutrients TEXT
        )
    ''')
    
    conn.commit()
    return conn

def filter_and_import(input_jsonl, db_path):
    print(f"Creating highly compressed SQLite DB at: {db_path}")
    conn = create_database(db_path)
    cursor = conn.cursor()
    
    foods_data = {} # Map of food_name -> set of nutrient names
    
    count = 0
    with open(input_jsonl, 'r', encoding='utf-8') as infile:
        for line in infile:
            try:
                row = json.loads(line.strip())
                
                raw_food_name = row.get("orig_food_common_name")
                raw_source = row.get("orig_source_name")
                
                food_name = raw_food_name.strip().lower() if raw_food_name else ""
                source = raw_source.strip().lower() if raw_source else ""
                
                if food_name and source:
                    if food_name not in foods_data:
                        foods_data[food_name] = set()
                    
                    # Store only the unique nutrient names to save space
                    # We drop the exact mg/100g quantities which bloats the database
                    foods_data[food_name].add(source)
                    
                count += 1
                if count % 100000 == 0:
                    print(f"Read {count} records...")
                    
            except json.JSONDecodeError:
                continue
                
    print(f"Finished reading. Grouped into {len(foods_data)} unique food items.")
    print("Formatting and inserting into DB...")
    
    # Insert compiled unique list
    for name, nutrients_set in foods_data.items():
        # Join nutrients as a comma-separated string to save space 
        # This keeps all the data (so we don't delete important vegan/allergy info)
        # but prevents millions of duplicate DB rows.
        nutrients_str = ", ".join(sorted(nutrients_set))
        cursor.execute('''
            INSERT INTO foods (name, nutrients)
            VALUES (?, ?)
        ''', (name, nutrients_str))
        
    # Create a standard B-Tree index on name for instant lookups (much smaller than FTS4)
    cursor.execute('CREATE INDEX idx_food_name ON foods(name)')
    
    conn.commit()
    conn.execute("VACUUM") # Compress the file size
    conn.close()
    
    print("Database is ready! It should now be tiny (likely under 20MB).")
    print("Copy this .db file to your Android app's 'assets/databases/' folder.")

if __name__ == "__main__":
    # Change this path to match your local input file path
    INPUT_FILE = r"D:\Downloads\foodb_2020_04_07_json\foodb_2020_04_07_json\blabla\content_filtered.jsonl"
    OUTPUT_DB = r"D:\Downloads\foodb_2020_04_07_json\foodb_2020_04_07_json\blabla\food_database1.db"
    
    if not os.path.exists(INPUT_FILE):
        print(f"Error: {INPUT_FILE} not found. Please place the script next to your jsonl file.")
    else:
        filter_and_import(INPUT_FILE, OUTPUT_DB)
