package com.markgoldenstein.zendesksms

import java.sql.Timestamp
import slick.driver.PostgresDriver.api._

object DB {
  class Users(tag: Tag) extends Table[(Long, Option[String], Option[String], Option[String], Timestamp, Timestamp)](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey)
    def firstName = column[Option[String]]("first_name")
    def lastName = column[Option[String]]("last_name")
    def email = column[Option[String]]("email")
    def createdOn = column[Timestamp]("created_on")
    def updatedOn = column[Timestamp]("updated_on")
    def * = (id, firstName, lastName, email, createdOn, updatedOn)
    def idx1 = index("users_created_on", createdOn)
    def idx2 = index("users_updated_on", updatedOn)
    def uq = index("users_email", email, unique = true)
  }
  val users = TableQuery[Users]

  class PhoneNumbers(tag: Tag) extends Table[(Long, Long, String)](tag, "phone_numbers") {
    def id = column[Long]("id", O.PrimaryKey)
    def userId = column[Long]("user_id")
    def number = column[String]("number")
    def * = (id, userId, number)
    def user = foreignKey("phone_numbers_user_id_fk", userId, users)(_.id)
    def uq = index("phone_numbers_number_uq", number, unique = true)
  }
  val phoneNumbers = TableQuery[PhoneNumbers]

  class MessageParts(tag: Tag) extends Table[(String, String, String, String, Timestamp, String, String, Int, Int)](tag, "message_parts") {
    def id = column[String]("id", O.PrimaryKey)
    def status = column[String]("status")
    def to = column[String]("to")
    def from = column[String]("from")
    def timestamp = column[Timestamp]("timestamp")
    def text = column[String]("text")
    def reference = column[String]("reference")
    def part = column[Int]("part")
    def total = column[Int]("total")
    def * = (id, status, to, from, timestamp, text, reference, part, total)
    def idx1 = index("message_parts_to_idx", to)
    def idx2 = index("message_parts_from_idx", from)
    def idx3 = index("message_parts_timestamp_idx", timestamp)
    def idx4 = index("message_parts_reference_idx", reference)
    def idx5 = index("message_parts_part_idx", part)
    def idx6 = index("message_parts_total_idx", total)
    def uq = index("message_parts_reference_part_uq", (reference, part), unique = true)
  }
  val messageParts = TableQuery[MessageParts]

  val schema = users.schema ++ phoneNumbers.schema ++ messageParts.schema
}
