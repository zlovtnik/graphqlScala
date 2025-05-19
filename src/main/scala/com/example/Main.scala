package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json._
import sangria.parser.QueryParser
import sangria.execution.Executor
import sangria.marshalling.sprayJson._
import scala.concurrent.Future
import scala.util.{Success, Failure}

object Main extends App with JsonProtocol {
  // Initialize ActorSystem
  implicit val system = ActorSystem(Behaviors.empty, "graphql-server")
  implicit val executionContext = system.executionContext

  // Initialize Database
  Database.init()
  println("Database initialized")

  // GraphQL route
  val route: Route = {
    path("graphql") {
      post {
        entity(as[JsValue]) { requestJson =>
          val query = requestJson.asJsObject.fields("query").asInstanceOf[JsString].value
          val operationName = requestJson.asJsObject.fields.get("operationName").collect {
            case JsString(op) => op
          }
          val variables = requestJson.asJsObject.fields.get("variables") match {
            case Some(vars: JsObject) => vars
            case _ => JsObject.empty
          }

          QueryParser.parse(query) match {
            case Success(queryAst) =>
              onSuccess(Executor.execute(
                SchemaDefinition.schema,
                queryAst,
                GraphQLContext(),
                variables = variables,
                operationName = operationName)) {
                result => complete(StatusCodes.OK -> result)
              }
            case Failure(error) =>
              complete(StatusCodes.BadRequest, JsObject(
                "error" -> JsString(error.getMessage))
              )
          }
        }
      }
    }
  }

  // Start server
  println("Starting server...")
  val bindingFuture = Http().newServerAt("0.0.0.0", 8080).bind(route)
  
  bindingFuture.onComplete {
    case Success(binding) =>
      val address = binding.localAddress
      println(s"Server online at http://${address.getHostString}:${address.getPort}/graphql")
    case Failure(ex) =>
      println(s"Failed to bind server: ${ex.getMessage}")
      system.terminate()
  }

  // Keep the application running
  scala.io.StdIn.readLine("Press ENTER to stop the server...\n")
  
  // Cleanup
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
