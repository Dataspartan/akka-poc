package com.dataspartan.akka.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.dataspartan.akka.backend.command.worker.executors.ChangeAddressProtocol.ChangeAddressResult
import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote
import com.dataspartan.akka.backend.entities.UserEntities.{User, Users}
import spray.json.DefaultJsonProtocol

trait RestJsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat4(User)
  implicit val usersJsonFormat = jsonFormat1(Users)
  implicit val addressJsonFormat = jsonFormat5(Address)
  implicit val insuranceQuoteJsonFormat = jsonFormat4(InsuranceQuote)

  implicit val actionPerformedJsonFormat = jsonFormat1(ChangeAddressResult)
}
