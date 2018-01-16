package com.dataspartan.akka.backend.query

object QueryProtocol {

  sealed trait UserRepoMsg
  final case object GetUsers extends UserRepoMsg
  final case class GetUser(userId: Long) extends UserRepoMsg
  final case class GetAddress(addressId: Long) extends UserRepoMsg

  sealed trait InsuranceServiceMsg
  final case class GetInsuranceQuote(quoteId: Long) extends InsuranceServiceMsg
}