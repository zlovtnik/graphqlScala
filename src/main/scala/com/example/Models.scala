package com.example

case class User(id: Int, name: String, email: String)
case class GraphQLContext()

object UserRepository {
  def getAllUsers: List[User] = {
    val connection = Database.getConnection
    val statement = connection.createStatement()
    val resultSet = statement.executeQuery("SELECT * FROM users")
    
    var users = List[User]()
    while (resultSet.next()) {
      users = User(
        resultSet.getInt("id"),
        resultSet.getString("name"),
        resultSet.getString("email")
      ) :: users
    }
    
    resultSet.close()
    statement.close()
    users.reverse
  }

  def getUserById(id: Int): Option[User] = {
    val connection = Database.getConnection
    val statement = connection.prepareStatement("SELECT * FROM users WHERE id = ?")
    statement.setInt(1, id)
    val resultSet = statement.executeQuery()
    
    val user = if (resultSet.next()) {
      Some(User(
        resultSet.getInt("id"),
        resultSet.getString("name"),
        resultSet.getString("email")
      ))
    } else {
      None
    }
    
    resultSet.close()
    statement.close()
    user
  }

  def createUser(name: String, email: String): User = {
    val connection = Database.getConnection
    val statement = connection.prepareStatement(
      "INSERT INTO users (name, email) VALUES (?, ?)",
      java.sql.Statement.RETURN_GENERATED_KEYS
    )
    
    statement.setString(1, name)
    statement.setString(2, email)
    statement.executeUpdate()
    
    val generatedKeys = statement.getGeneratedKeys
    val userId = if (generatedKeys.next()) {
      generatedKeys.getInt(1)
    } else {
      throw new RuntimeException("Failed to create user")
    }
    
    generatedKeys.close()
    statement.close()
    
    User(userId, name, email)
  }
}
