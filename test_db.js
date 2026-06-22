const sqlite3 = require('sqlite3').verbose();
const db = new sqlite3.Database('app/src/main/assets/databases/MasterUnifiedDB.db', sqlite3.OPEN_READONLY, (err) => {
    if (err) {
        console.error("Open err:", err.message);
    }
    console.log('Connected to the database.');
});
db.serialize(() => {
    db.all("PRAGMA integrity_check;", (err, rows) => {
        if (err) console.error("Integrity err:", err);
        else console.log("Integrity:", rows);
    });
    db.all("SELECT name FROM sqlite_master WHERE type='table';", (err, rows) => {
        if (err) console.error("Table err:", err);
        else console.log("Tables:", rows);
    });
});
db.close();
