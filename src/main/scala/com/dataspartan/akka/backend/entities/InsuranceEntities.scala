package com.dataspartan.akka.backend.entities

import com.dataspartan.akka.backend.entities.AddressEntities.Address
import com.dataspartan.akka.backend.entities.UserEntities.User
import slick.jdbc.H2Profile.api._

import scala.util.Random

object InsuranceEntities {
  final case class InsuranceQuote(userId: Long, quantity : Double, description: String, address: Address, quoteId: Option[Long] = None)

  final case class InsuranceQuoteDB(userId: Long, addressId: Long, quantity : Double, description: String,
                                    quoteId: Option[Long] = None) {

    def toInsuranceQuote(address: Address): InsuranceQuote =
      InsuranceQuote(userId, quantity, description, address, quoteId)
  }

  final object InsuranceQuoteDBFactory {
    def fromInsuranceQuote(insuranceQuote: InsuranceQuote) =
      InsuranceQuoteDB(insuranceQuote.userId, insuranceQuote.address.addressId.get, insuranceQuote.quantity,
        insuranceQuote.description, None)

    def generateQuote(user: User, address: Address): InsuranceQuote = {
      InsuranceQuote(user.userId.get,
        Random.nextDouble(),
        s"Quote for new address $address for the user ${user.surname}, ${user.name}",
        address)
    }
  }

  class InsuranceQuotesDB(tag: Tag) extends Table[InsuranceQuoteDB](tag, "INSURANCE_QUOTES") {
    // Auto Increment the id primary key column
    def quoteId = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("USER_ID")
    def addressId = column[Long]("ADDRESS_ID")
    def quantity = column[Double]("QUANTITY")
    def description = column[String]("DESCRIPTION")

    def user = foreignKey("IQ_USER_FK", userId, UserEntities.usersDB)(_.userId,
      onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    def address = foreignKey("IQ_ADDRESS_FK", addressId, AddressEntities.addressesDB)(_.addressId,
      onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    // the * projection (e.g. select * ...) auto-transforms the tupled
    def * = (userId, addressId, quantity, description, quoteId.?) <> (InsuranceQuoteDB.tupled, InsuranceQuoteDB.unapply)
  }
  val insuranceQuotesDB = TableQuery[InsuranceQuotesDB]
}