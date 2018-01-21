package com.dataspartan.akka.backend.query

object QueryProtocol {

  sealed trait Query

  sealed trait QueryFailed extends Query {
    val error: Throwable
  }

  sealed trait UserQuery extends Query
  final case object GetUsers extends UserQuery
  final case class GetUser(userId: Long) extends UserQuery
  final case class GetAddress(addressId: Long) extends UserQuery
  final case object UserNotFound extends UserQuery
  final case object AddressNotFound extends UserQuery
  final case class UserQueryFailed(override val error: Throwable) extends QueryFailed

  sealed trait InsuranceQuery extends Query
  final case class GetInsuranceQuote(quoteId: Long) extends InsuranceQuery
  final case object InsuranceQuoteNotFound extends InsuranceQuery
  final case class InsuranceQueryFailed(override val error: Throwable) extends QueryFailed
}