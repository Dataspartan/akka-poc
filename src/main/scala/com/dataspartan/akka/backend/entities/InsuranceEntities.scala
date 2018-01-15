package com.dataspartan.akka.backend.entities

import com.dataspartan.akka.backend.entities.AddressEntities.Address

import slick.jdbc.H2Profile.api._

object InsuranceEntities {
  final case class InsuranceQuote(userId: Long, quantity : Double, description: String, address: Address, quoteId: Option[Long])

  final case class InsuranceQuoteDB(userId: Long, addressId: Long, quantity : Double, description: String,
                                    quoteId: Option[Long] = None)

  final object InsuranceQuoteDBTrans {
    def fromInsuranceQuote(insuranceQuote: InsuranceQuote, address: Address) =
      InsuranceQuoteDB(insuranceQuote.userId, insuranceQuote.address.addressId.get, insuranceQuote.quantity,
        insuranceQuote.description, None)

    def toInsuranceQuote(insQuoteDB: InsuranceQuoteDB, address: Address) =
      InsuranceQuote(insQuoteDB.userId, insQuoteDB.quantity, insQuoteDB.description, address, insQuoteDB.quoteId)
  }

  class InsuranceQuotesDB(tag: Tag) extends Table[InsuranceQuoteDB](tag, "INSURANCE_QUOTES") {
    // Auto Increment the id primary key column
    def quoteId = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("USER_ID")
    def addressId = column[Long]("ADDRESS_ID")
    def quantity = column[Double]("QUANTITY")
    def description = column[String]("DESCRIPTION")

    def user = foreignKey("IQ_USER_FK", userId, UserEntities.usersDB)(_.userId,
      onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
    def address = foreignKey("IQ_ADDRESS_FK", addressId, AddressEntities.addressesDB)(_.addressId,
      onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
    // the * projection (e.g. select * ...) auto-transforms the tupled
    def * = (userId, addressId, quantity, description, quoteId.?) <> (InsuranceQuoteDB.tupled, InsuranceQuoteDB.unapply)
  }
  val insuranceQuotesDB = TableQuery[InsuranceQuotesDB]
}