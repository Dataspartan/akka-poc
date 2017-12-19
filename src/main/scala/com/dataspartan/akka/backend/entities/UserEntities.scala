package com.dataspartan.akka.backend.entities

object UserEntities {
  final case class Users(users: Seq[User])
  final case class User(userId: String, login: String, name: String, surname: String)
}