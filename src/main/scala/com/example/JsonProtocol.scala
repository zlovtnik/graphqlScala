package com.example

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

trait JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  // Convert between User and JSON
  implicit val userFormat: RootJsonFormat[User] = jsonFormat3(User.apply)

  // Custom formatter for handling GraphQL responses
  implicit object GraphQLResponseJsonWriter extends RootJsonWriter[Any] {
    def write(obj: Any): JsValue = obj match {
      case map: Map[_, _] => JsObject(
        map.asInstanceOf[Map[String, Any]].map {
          case (key, value) => key -> write(value)
        }
      )
      case seq: Seq[_] => JsArray(seq.map(write).toVector)
      case str: String => JsString(str)
      case num: Int => JsNumber(num)
      case num: Double => JsNumber(num)
      case bool: Boolean => JsBoolean(bool)
      case null => JsNull
      case user: User => userFormat.write(user)
      case other => throw new RuntimeException(s"Unexpected type: ${other.getClass}")
    }
  }
}
