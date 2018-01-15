package com.dataspartan.akka.backend.entities

import slick.jdbc.H2Profile.api._

object AddressEntities {

  final case class Address(number: String, street: String, town: String, county: String, postcode: String,
                           addressId: Option[Long] = None)

  final case class AddressDB(number: String, street: String, town: String, county: String, postcode: String,
                             addressId: Option[Long] = None)
  final object AddressDBTrans {
    def fromAddress(address: Address): AddressDB = AddressDB(address.number, address.street, address.town,
      address.county, address.postcode, address.addressId)
  }

  class AddressesDB(tag: Tag) extends Table[AddressDB](tag, "ADDRESS") {
    // Auto Increment the id primary key column
    def addressId = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def number = column[String]("NUMBER")
    def street = column[String]("STREET")
    def town = column[String]("TOWN")
    def county = column[String]("COUNTY")
    def postcode = column[String]("POSTCODE")

    // the * projection (e.g. select * ...) auto-transforms the tupled
    def * = (number, street, town, county, postcode, addressId.?) <> (AddressDB.tupled, AddressDB.unapply)
  }
  val addressesDB = TableQuery[AddressesDB]
}