package com.dataspartan.akka.http

object RestMessage {

  final case class Users(users: Seq[User])
  final case class User(login: String, name: String, surname: String)
  final case class Address(number: String, street: String, town: String, county: String, postcode: String)
  final case class InsuranceQuote(quoteId: String, quantity : Double, description: String, address: Address)

  final case class ActionPerformed(description: String)
}