import sqlite3
import os
import glob

def merge_sqlite_databases(output_db="MergedFoodDB.db"):
    """
    Finds all .db files in the current directory and merges their tables
    into a single SQLite database.
    """
    if os.path.exists(output_db):
        os.remove(output_db)
        
    print(f"Creating merged database: {output_db}")
    main_conn = sqlite3.connect(output_db)
    main_cursor = main_conn.cursor()
    
    # Get all .db files except the output file
    db_files = [f for f in glob.glob("*.db") if f != output_db]
    
    if not db_files:
        print("No .db files found in the current directory.")
        return

    for db_file in db_files:
        print(f"\nProcessing database: {db_file}")
        
        # Create a safe alias for attaching the database
        db_alias = db_file.replace('.db', '').replace('-', '_').replace(' ', '_')
        
        # Connect temporarily to find the tables within this DB
        temp_conn = sqlite3.connect(db_file)
        temp_cursor = temp_conn.cursor()
        temp_cursor.execute("SELECT name, sql FROM sqlite_master WHERE type='table';")
        tables = temp_cursor.fetchall()
        temp_conn.close()
        
        # Attach the database to our main connection
        main_cursor.execute(f"ATTACH DATABASE '{db_file}' AS {db_alias};")
        
        for table_name, table_sql in tables:
            if table_name == 'sqlite_sequence':
                continue
                
            print(f"  -> Copying table: '{table_name}'")
            
            try:
                # Attempt to recreate the exact schema (preserves primary keys and constraints)
                if table_sql:
                     main_cursor.execute(table_sql)
                else:
                    # Fallback if no SQL is available
                    main_cursor.execute(f"CREATE TABLE '{table_name}' AS SELECT * FROM {db_alias}.'{table_name}';")

                # Insert the data
                main_cursor.execute(f"INSERT INTO '{table_name}' SELECT * FROM {db_alias}.'{table_name}';")
                
            except sqlite3.OperationalError as e:
                # If there's a table name collision (table already exists)
                if 'already exists' in str(e).lower():
                    new_table_name = f"{db_alias}_{table_name}"
                    print(f"     [!] Table '{table_name}' already exists. Renaming to '{new_table_name}'.")
                    
                    # We can't easily rewrite the exact schema string for the new name safely,
                    # so we fallback to CREATE TABLE AS for the duplicate table name
                    main_cursor.execute(f"CREATE TABLE '{new_table_name}' AS SELECT * FROM {db_alias}.'{table_name}';")
                else:
                    print(f"     [!] Error processing table '{table_name}': {e}")
            except Exception as e:
                 print(f"     [!] Unexpected error: {e}")

        # Commit any pending transactions before detaching
        main_conn.commit()
        # Detach the source database
        main_cursor.execute(f"DETACH DATABASE {db_alias};")

    print(f"\nSuccessfully merged {len(db_files)} databases into '{output_db}'.")
    print("Optimization: Vacuuming the new database to compress its size...")
    main_conn.execute("VACUUM;")
    main_conn.commit()
    main_conn.close()
    
    print("Done! You can now place this Merged database into your Android App's assets folder.")

if __name__ == "__main__":
    merge_sqlite_databases()
