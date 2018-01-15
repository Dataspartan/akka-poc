package com.dataspartan.akka.http.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import com.dataspartan.akka.backend.entities.InsuranceEntities.InsuranceQuote
import akka.pattern.ask
import com.dataspartan.akka.backend.query.QueryProtocol.GetInsuranceQuote

import scala.concurrent.Future

trait InsuranceManagementRoutes extends RestRoutes {

  //#all-routes
  lazy val insuranceManagementRoutes: Route = handleExceptions(routeExceptionHandler) {
    pathPrefix("insuranceQuotes") {
      concat(
        path(LongNumber) { quoteId => insuranceQuoteRoute(quoteId) }
      )
    }
  }
  //#all-routes

  def insuranceQuoteRoute(quoteId: Long): Route =
    concat(
      get {
        log.info(s"get info for quote - $quoteId")
        val maybeInsuranceQuote: Future[Option[InsuranceQuote]] =
          (queryMasterProxy ? GetInsuranceQuote(quoteId)).mapTo[Option[InsuranceQuote]]
        rejectEmptyResponse {
          complete(maybeInsuranceQuote)
        }
      }
    )
}
