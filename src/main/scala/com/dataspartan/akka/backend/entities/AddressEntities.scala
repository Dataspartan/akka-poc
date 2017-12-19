package com.dataspartan.akka.backend.entities

object AddressEntities {
  final case class Address(number: String, street: String, town: String, county: String, postcode: String)
}