package com.dataspartan.akka.http.routes

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote
import com.dataspartan.akka.backend.query.InsuranceQuotingService.GetInsuranceQuote
import akka.pattern.ask
import scala.concurrent.Future

trait InsuranceManagementRoutes extends RestRoutes {

  def insuranceQuotingService: ActorRef

  //#all-routes
  lazy val insuranceManagementRoutes: Route =
    pathPrefix("insuranceQuotes") {
      concat(
        path(Segment) { quoteId => insuranceQuoteRoute(quoteId) },
      )
    }
  //#all-routes

  def insuranceQuoteRoute(quoteId: String): Route =
    concat(
      get {
          log.info(s"get info for quote - $quoteId")
        val maybeInsuranceQuote: Future[Option[InsuranceQuote]] =
          (insuranceQuotingService ? GetInsuranceQuote(quoteId)).mapTo[Option[InsuranceQuote]]
        rejectEmptyResponse {
          complete(maybeInsuranceQuote)
        }
      }
    )
}
