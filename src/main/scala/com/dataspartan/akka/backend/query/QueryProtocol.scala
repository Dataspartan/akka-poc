package com.dataspartan.akka.backend.query

object QueryProtocol {

  sealed trait Query

  sealed trait UserQuery extends Query
  final case object GetUsers extends UserQuery
  final case class GetUser(userId: Long) extends UserQuery
  final case class GetAddress(addressId: Long) extends UserQuery

  sealed trait InsuranceQuery extends Query
  final case class GetInsuranceQuote(quoteId: Long) extends InsuranceQuery
}