package com.markgoldenstein.zendesksms

import scala.language.postfixOps

import scala.concurrent._
import akka.actor.Actor
import spray.routing._
import spray.http._
import spray.json._
import spray.httpx.marshalling.ToResponseMarshallable
import MyJsonProtocol._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class WebhookServiceActor extends Actor with WebhookService {

  import context.dispatcher

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)

  override def sendSQSMessageAndReturnStatusCode(nexmoMessage: NexmoMessage) = nexmoMessage.timestamp match {
    case Util.nexmoTimestampRegex(_*) if nexmoMessage.from.nonEmpty && nexmoMessage.text.nonEmpty =>
      Util.incomingQueue.send(nexmoMessage.toJson.prettyPrint) map {
        case _ => StatusCodes.OK
      } recover {
        case _ => StatusCodes.ServiceUnavailable
      }
    case _ =>
      // ignore the sms if there is no sender phone number or the message is empty
      Future(StatusCodes.OK)
  }
}


// this trait defines our service behavior independently from the service actor
trait WebhookService extends HttpService {
  def sendSQSMessageAndReturnStatusCode(sqsMessage: NexmoMessage): ToResponseMarshallable

  val myRoute =
    path("vVH63xHsCGLWHj8h3trFHCATqzZzGPbA44gwjsbuVxHZWZ3rEufSAQ8hwdKJUks3") {
      post {
        formFields(
          'type,
          'to, // to number
          'msisdn ?, // from number
          'messageId, // internal nexmo id
          "message-timestamp",
          'text ?,
          'keyword ?,
          'concat.as[Boolean] ?,
          "concat-ref" ?,
          "concat-total".as[Int] ?,
          "concat-part".as[Int] ?,
          'data ?,
          'udh ?).as(NexmoMessage) { m =>
          complete {
            (m.concat, m.concatRef, m.concatPart, m.concatTotal) match {
              case (Some(true), Some(_), Some(_), Some(_)) =>
                sendSQSMessageAndReturnStatusCode(m)
              case (Some(false), _, _, _) =>
                sendSQSMessageAndReturnStatusCode(m)
              case (None, _, _, _) =>
                sendSQSMessageAndReturnStatusCode(m)
              case _ =>
                throw InvalidStateException(s"Invalid parameters: ${m.toJson.prettyPrint}")
            }
          }
        }
      }
    } ~
  path("eQbxIxYaymiwhRiibJgXITDHUtOWTOHVyNFnaYURScbgfeRTVBGISGPalmAmwdqt") {
    post {
      formFields(
        'ticketId.as[Long],
        'from,
        'to,
        'text) { (ticketId, from, to, text) =>
        complete {
          StatusCodes.OK
        }
      }
    }
  }
}