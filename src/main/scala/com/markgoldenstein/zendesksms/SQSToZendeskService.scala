package com.markgoldenstein.zendesksms


import scala.language.postfixOps

import java.sql.Timestamp
import java.time.{ZoneOffset, ZonedDateTime}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.actor.{ActorLogging, Actor}
import akka.io.IO
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import slick.driver.PostgresDriver.api._
import spray.can.Http
import spray.http._
import spray.http.Uri._
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.client.pipelining._
import MyJsonProtocol._
import Util.db
import DB._

case object CheckAllMessagePartsInDB
case class CheckMessagePartsInDBForRef(referene: String)

case class InvalidStateException(message: String) extends Exception(message)
case class UserNotCreatedException(message: String) extends Exception(message)
case class SearchResultsNotRetrievedException(message: String) extends Exception(message)
case class TicketNotCreatedException(message: String) extends Exception(message)

class SQSToZendeskServiceActor extends Actor with ActorLogging {
  import context.system
  import context.dispatcher
  implicit val timeout = Timeout(10 seconds)

  val pipeline: Future[SendReceive] =
    for (
      Http.HostConnectorInfo(connector, _) <-
      IO(Http) ? new Http.HostConnectorSetup(
        host = Config.zendeskSite,
        port = 443,
        sslEncryption = true)
    ) yield (
      addCredentials(BasicHttpCredentials(s"${Config.zendeskUserEmail}/token", Config.zendeskUserToken))
        ~> sendReceive(connector)
    )

  val base = Uri("/api/v2")

  def receive = {
    case m: NexmoMessage =>
      m.concat match {
        case Some(true) =>
          processMessagePart(m)
        case _ =>
          sendMessageToZendesk(m)
      }

    case CheckMessagePartsInDBForRef(ref) =>
      checkMessagePartsInDBForRef(ref)

    case CheckAllMessagePartsInDB =>
      checkAllMessagePartsInDB()
  }

  private def processMessagePart(message: NexmoMessage): Unit = {
    val requestor = sender()
    message.timestamp match {
      case Util.nexmoTimestampRegex(year, month, dayOfMonth, hour, minute, second) =>
        val millis = ZonedDateTime.of(year.toInt, month.toInt, dayOfMonth.toInt, hour.toInt, minute.toInt, second.toInt, 0, ZoneOffset.UTC).toInstant.toEpochMilli
        val timestamp = new Timestamp(millis)

        val f = db.run(
          messageParts += (message.id, "queued", message.to, message.from.get, timestamp, message.text.get, message.concatRef.get, message.concatPart.get, message.concatTotal.get)
        )

        f onSuccess { case _ =>
          self ! CheckMessagePartsInDBForRef(message.concatRef.get)
          requestor ! "Done!"
        }

        f onFailure { case e =>
          throw e
        }
    }
  }

  val query = messageParts.groupBy(m => (m.reference, m.total))
                          .map { case (group, m) =>
                             (group._1, group._2, m.length, m.map(_.part).min, m.map(_.part).max) }
                          .filter { case (reference, total, count, minPart, maxPart) =>
                             total === count && minPart === 1 && maxPart === total }

  private def checkMessagePartsInDBForRef(reference: String): Unit = {
    val q = messageParts.filter(_.reference in query.filter(_._1 === reference).map(_._1))
                        .length
                        .result

    val f = db.run(q) flatMap { case count if count > 0  =>

      val q = messageParts.filter(m => m.reference === reference && m.status === "queued")
                          .map(_.status)
                          .update("processing")
      db.run(q)

    } flatMap { case rows if rows > 0 =>

      val q = messageParts.filter(_.reference === reference)
                          .sortBy(_.part.asc)
                          .map(m => (m.to, m.from, m.id, m.timestamp, m.text))
                          .result
      db.run(q)

    } flatMap { case seq =>

      val text = seq.map(_._5).mkString
      val nexmoMessage = NexmoMessage(to = seq(0)._1,
                           from = Some(seq(0)._2),
                           id = seq(0)._3,
                           timestamp = Util.dateFormat.format(seq(0)._4),
                           text = Some(text))
      Util.incomingQueue.send(nexmoMessage.toJson.prettyPrint)
    }

    f onSuccess { case _ =>
      val q = messageParts.filter(_.reference === reference)
                          .delete
      db.run(q)
    }

    f onFailure { case e =>
      val q = messageParts.filter(m => m.reference === reference && m.status === "processing")
                          .map(_.status)
                          .update("queued")
      db.run(q)

      throw e
    }
  }

  private def checkAllMessagePartsInDB(): Unit = {
    val q = query.map(_._1)
                 .result

    val f = db.run(q).map(_ foreach { case reference =>
      self ! CheckMessagePartsInDBForRef(reference)
    })

    f onFailure { case e =>
      throw e
    }
  }

  private def sendMessageToZendesk(m: NexmoMessage): Unit = {
    val userEmail = Util.phoneToEmail(m.from.get)
    val textMessage: String = m.text.get

    val searchResponseUser: Future[HttpResponse] =
      pipeline.flatMap(_(Get(base + "/users/search.json" withQuery ("query" -> userEmail))))

    // log.info(Await.result(searchResponseUser, 10 seconds).toString)

    val searchResultsUser = searchResponseUser map {
      case r if r.status == StatusCodes.OK =>
        r.entity.asString.parseJson.asJsObject.fields("users").convertTo[JsArray].elements
      case r =>
        throw SearchResultsNotRetrievedException(r.toString)
    }

    val result: Future[String] = searchResultsUser flatMap {
      case usersJson if usersJson.size > 1 =>
        throw InvalidStateException(s"Found more than one user for query [$userEmail].")
      case usersJson if usersJson.size == 1 =>
        val userId = usersJson.head.asJsObject.fields("id").convertTo[Long]
        val viewPreviewResponse: Future[HttpResponse] =
          pipeline.flatMap(_(Post(base + "/views/preview.json", ViewRequest(View(Seq(
            ViewCondition(field = "status", operator = "less_than", value = "solved"),
            ViewCondition(field = "requester_id", operator = "is", value = userId.toString)
          ), sort_by = "updated_at", sort_order = "desc")))))

        // log.info(Await.result(viewPreviewResponse, 10 seconds).toString)

        val viewPreviewResults = viewPreviewResponse map {
          case r if r.status == StatusCodes.OK =>
            r.entity.asString.parseJson.asJsObject.fields("rows").convertTo[JsArray].elements
          case r =>
            throw SearchResultsNotRetrievedException(r.toString)
        }

        viewPreviewResults flatMap {
          case searchResults if searchResults.size == 0 =>
            createNewTicket(userId, textMessage)
          case searchResults =>
            val ticketId = searchResults.head.asJsObject.fields("ticket").asJsObject.fields("id").convertTo[Long]
            commentOnExistingTicket(ticketId, userEmail, textMessage)
        }
      case usersJson if usersJson.isEmpty =>
        val createUserResponse: Future[HttpResponse] =
          pipeline.flatMap(_(Post(base + "/users.json", CreateUserRequest(CreateUser(
            name = m.from.get.toString,
            email = userEmail,
            verified = true,
            phone = m.from.get.toString
          )))))

        createUserResponse flatMap {
          case r if r.status == StatusCodes.Created =>
            val userId = r.entity.asString.parseJson.asJsObject.fields("user").asJsObject.fields("id").convertTo[Long]
            createNewTicket(userId, textMessage)
          case r =>
            throw UserNotCreatedException(r.toString)
        }
    }

    pipe(result) to sender()
  }

  private def createNewTicket(userId: Long, textMessage: String): Future[String] = {
    val ticket = TicketRequest(Ticket(
      requester_id = userId,
      subject = Some(textMessage.take(80)),
      comment = Comment(textMessage)
    ))

    val ticketResponse: Future[HttpResponse] =
      pipeline.flatMap(_(Post(base + "/tickets.json", ticket)))

    ticketResponse map {
      case r if r.status == StatusCodes.Created => "Done!"
      case r => throw TicketNotCreatedException(r.toString)
    }
  }

  private def commentOnExistingTicket(ticketId: Long, userEmail: String, textMessage: String): Future[String] = {
    val userPipeline: Future[SendReceive] =
      for (
        Http.HostConnectorInfo(connector, _) <-
        IO(Http) ? new Http.HostConnectorSetup(
          host = Config.zendeskSite,
          port = 443,
          sslEncryption = true)
      ) yield (
        addCredentials(BasicHttpCredentials(s"$userEmail/token", Config.zendeskUserToken))
          ~> sendReceive (connector)
      )

    val ticketComment = UserCommentRequest(UserComment(
      UserCommentValue(textMessage)
    ))

    val ticketCommentResponse: Future[HttpResponse] =
      userPipeline.flatMap(_(Put(base + s"/requests/$ticketId.json", ticketComment)))

    // log.info(Await.result(ticketCommentResponse, 10 seconds).toString)

    ticketCommentResponse map {
      case r if r.status == StatusCodes.OK => "Done!"
      case r => throw TicketNotCreatedException(r.toString)
    }
  }
}
