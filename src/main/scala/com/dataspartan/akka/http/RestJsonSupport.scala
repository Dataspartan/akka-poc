package com.dataspartan.akka.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait RestJsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._
  import com.dataspartan.akka.http.RestMessage._

  implicit val userJsonFormat = jsonFormat3(User)
  implicit val usersJsonFormat = jsonFormat1(Users)
  implicit val addressJsonFormat = jsonFormat5(Address)
  implicit val insuranceQuoteJsonFormat = jsonFormat4(InsuranceQuote)

  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
}
