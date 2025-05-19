package com.example

import sangria.schema._

object SchemaDefinition {
  // Define User type
  val UserType = ObjectType(
    "User",
    fields[Unit, User](
      Field("id", IntType, resolve = _.value.id),
      Field("name", StringType, resolve = _.value.name),
      Field("email", StringType, resolve = _.value.email)
    )
  )

  // Define queries
  val QueryType = ObjectType(
    "Query",
    fields[GraphQLContext, Unit](
      Field("users", ListType(UserType),
        description = Some("Returns a list of all users"),
        resolve = c => UserRepository.getAllUsers
      ),
      Field("user", OptionType(UserType),
        description = Some("Returns a user by ID"),
        arguments = List(Argument("id", IntType)),
        resolve = c => UserRepository.getUserById(c.arg[Int]("id"))
      )
    )
  )

  // Define mutations
  val MutationType = ObjectType(
    "Mutation",
    fields[GraphQLContext, Unit](
      Field("createUser", UserType,
        arguments = List(
          Argument("name", StringType),
          Argument("email", StringType)
        ),
        resolve = c => UserRepository.createUser(
          c.arg[String]("name"),
          c.arg[String]("email")
        )
      )
    )
  )

  // Schema
  val schema = Schema(QueryType, Some(MutationType))
}
