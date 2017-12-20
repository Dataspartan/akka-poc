package com.dataspartan.akka.backend.query

object QueryProtocol {

  sealed trait UserRepoMsg
  final case object GetUsers extends UserRepoMsg
  final case class GetUser(userId: String) extends UserRepoMsg
  final case class GetAddress(userId: String) extends UserRepoMsg

  sealed trait InsuranceServiceMsg
  final case class GetInsuranceQuote(quoteId: String) extends InsuranceServiceMsg
}