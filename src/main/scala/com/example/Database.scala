package com.example

import java.sql.{Connection, DriverManager}

object Database {
  private var connection: Connection = _

  def init(): Unit = {
    // Load the SQLite JDBC driver
    Class.forName("org.sqlite.JDBC")
    connection = DriverManager.getConnection("jdbc:sqlite:database.db")
    createTables()
  }

  private def createTables(): Unit = {
    val statement = connection.createStatement()
    // Create a sample table for users
    statement.execute("""
      CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        email TEXT NOT NULL UNIQUE
      )
    """)
    statement.close()
  }

  def getConnection: Connection = connection
}
