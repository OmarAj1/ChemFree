package com.example

import org.junit.Test
import java.io.File
import java.sql.DriverManager

class ExampleUnitTest {
  @Test
  fun testInspectDatabase() {
    val dbFile = File("src/main/assets/databases/MasterUnifiedDB.db")
    if (!dbFile.exists()) {
      println("DB file does not exist at: ${dbFile.absolutePath}")
      return
    }
    val url = "jdbc:sqlite:${dbFile.absolutePath}"
    try {
      Class.forName("org.sqlite.JDBC")
      DriverManager.getConnection(url).use { conn ->
        conn.metaData.getTables(null, null, "%", null).use { rs ->
          while (rs.next()) {
             val tableName = rs.getString("TABLE_NAME")
             println("Table: $tableName")
             // List columns
             conn.createStatement().use { stmt ->
               stmt.executeQuery("PRAGMA table_info('$tableName')").use { colRs ->
                 while (colRs.next()) {
                   val colName = colRs.getString("name")
                   val colType = colRs.getString("type")
                   println("  - $colName ($colType)")
                 }
               }
             }
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
