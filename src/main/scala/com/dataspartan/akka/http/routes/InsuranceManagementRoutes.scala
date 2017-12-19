package com.dataspartan.akka.http.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import com.dataspartan.akka.http.RestMessage.{Address, InsuranceQuote}

trait InsuranceManagementRoutes extends RestRoutes {

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
          complete(InsuranceQuote(quoteId, 100, "description",
            Address("number", s"street $quoteId", "town", "county", "postcode")))
      }
    )
}
