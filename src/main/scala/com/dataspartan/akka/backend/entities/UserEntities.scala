package com.dataspartan.akka.backend.entities

import slick.jdbc.H2Profile.api._


object UserEntities {

  final case class Users(users: Seq[User])

  final case class User(login: String, name: String, surname: String, userId: Option[Long] = None,
                        addressId: Option[Long] = None)

  final case class UserDB(login: String, name: String, surname: String, userId: Option[Long] = None,
                          addressId: Option[Long] = None) {
    def toUser: User = User(login, name, surname, userId, addressId)
  }

  final object UserDBFactory {
    def fromUser(user: User) = UserDB(user.login, user.name, user.surname, user.userId, user.addressId)
  }

  class UsersDB(tag: Tag) extends Table[UserDB](tag, "USER") {
    // Auto Increment the id primary key column
    def userId = column[Long]("ID", O.PrimaryKey, O.AutoInc)

    // The login can't be null
    def login = column[String]("LOGIN", O.Unique)

    // The name can't be null
    def name = column[String]("NAME")

    // The surname can't be null
    def surname = column[String]("SURNAME")

    def addressId = column[Option[Long]]("ADDRESS_ID")

    def address = foreignKey("ADDRESS_FK", addressId, AddressEntities.addressesDB)(_.addressId.?,
      onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)

    // the * projection (e.g. select * ...) auto-transforms the tupled
    // column values to / from a User
    def * = (login, name, surname, userId.?, addressId) <> (UserDB.tupled, UserDB.unapply)
  }

  val usersDB = TableQuery[UsersDB]
}