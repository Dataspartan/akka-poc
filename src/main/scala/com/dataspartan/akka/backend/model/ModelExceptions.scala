package com.dataspartan.akka.backend.model

object ModelExceptions {
  case class InstanceNotFoundException(private val message: String = "Instance not found", private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
  case class DataAccessException(private val message: String = "", private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
}
