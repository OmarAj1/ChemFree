package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BioNexa", appName)
  }

  @Test
  fun `print merged database tables`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = DatabaseManager.openDatabase(context, "MergedFoodDB.db")
    if (db != null) {
      println("--- COLUMNS IN foods TABLE ---")
      db.rawQuery("PRAGMA table_info('foods')", null).use { cursor ->
        while (cursor.moveToNext()) {
          val name = cursor.getString(1)
          val type = cursor.getString(2)
          println("  - $name ($type)")
        }
      }
      println("--- END COLUMNS ---")
      db.close()
    } else {
      println("Could not open MergedFoodDB.db")
    }
  }
}
