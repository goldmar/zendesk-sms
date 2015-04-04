package com.markgoldenstein.zendesksms

import spray.json._

case class NexmoMessage(`type`: String = "text",
                        to: String,
                        from: Option[String],
                        id: String,
                        timestamp: String,
                        text: Option[String],
                        keyword: Option[String] = None,
                        concat: Option[Boolean] = None,
                        concatRef: Option[String] = None,
                        concatTotal: Option[Int] = None,
                        concatPart: Option[Int] = None,
                        data: Option[String] = None,
                        udh: Option[String] = None)

case class CreateUserRequest(user: CreateUser)
case class CreateUser(name: String, email: String, verified: Boolean = false, phone: String)

case class TicketRequest(ticket: Ticket)
case class Ticket(requester_id: Long,
                  subject: Option[String] = None,
                  comment: Comment,
                  status: Option[TicketStatus.Status] = None,
                  custom_fields: Option[Seq[CustomField]] = None)
case class Comment(body: String)
object TicketStatus extends Enumeration {
  type Status = Value
  val New = Value("new")
  val Open = Value("open")
  val Pending = Value("pending")
  val Solved = Value("solved")
  val Closed = Value("closed")
}
trait CustomField
case class StringField(id: Long, value: String) extends CustomField
case class NumericalField(id: Long, value: Long) extends CustomField

case class UserCommentRequest(request: UserComment)
case class UserComment(comment: UserCommentValue)
case class UserCommentValue(value: String)

case class ViewRequest(view: View)
case class View(all: Seq[ViewCondition], sort_by: String, sort_order: String)
case class ViewCondition(field: String, operator: String, value: String)

object MyJsonProtocol extends DefaultJsonProtocol {
  def jsonEnum[T <: Enumeration](enu: T) = new JsonFormat[T#Value] {
    def write(obj: T#Value) = JsString(obj.toString)
    def read(json: JsValue) = json match {
      case JsString(txt) => enu.withName(txt)
      case something => throw new DeserializationException(s"Expected a value from enum $enu instead of $something")
    }
  }

  implicit val nexmoMessageFormat = jsonFormat13(NexmoMessage)

  implicit val createUserFormat = jsonFormat4(CreateUser)
  implicit val createUserRequestFormat = jsonFormat1(CreateUserRequest)

  implicit val numericalFieldFormat = jsonFormat2(NumericalField)
  implicit val stringFieldFormat = jsonFormat2(StringField)

  implicit object CustomFieldFormat extends JsonFormat[CustomField] {
    def write(value: CustomField) = value match {
      case v: StringField => v.toJson
      case v: NumericalField => v.toJson
    }
    def read(value: JsValue) =
      value.asJsObject.fields("value") match {
        case JsString(_) => value.convertTo[StringField]
        case JsNumber(_) => value.convertTo[NumericalField]
        case _ => throw new DeserializationException("CustomField expected")
      }
  }

  implicit val ticketStatusFormat = jsonEnum(TicketStatus)
  implicit val commentFormat = jsonFormat1(Comment)
  implicit val ticketFormat = jsonFormat5(Ticket)
  implicit val ticketRequestFormat = jsonFormat1(TicketRequest)

  implicit val userCommentValueFormat = jsonFormat1(UserCommentValue)
  implicit val userCommentFormat = jsonFormat1(UserComment)
  implicit val userCommentRequestFormat = jsonFormat1(UserCommentRequest)

  implicit val viewConditionFormat = jsonFormat3(ViewCondition)
  implicit val viewFormat = jsonFormat3(View)
  implicit val viewRequestFormat = jsonFormat1(ViewRequest)
}
