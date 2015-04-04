package com.markgoldenstein.zendesksms

import java.text.SimpleDateFormat

import scala.language.postfixOps

import scala.concurrent.duration._
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import slick.driver.PostgresDriver.api._
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.kifi.franz.{SQSMessage, QueueName, SimpleSQSClient}
import com.typesafe.config.ConfigFactory
import play.api.libs.iteratee.Iteratee
import spray.can.Http
import spray.json._
import MyJsonProtocol._

object Boot extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")
  import system.dispatcher

  if (Config.createTables) {
    Util.db.run(DBIO.seq(
      // DB.schema.drop,
      DB.schema.create
    ))
  }

  // create and start our service actor
  val webhookToSQSService = system.actorOf(Props[WebhookServiceActor], "webhook")

  implicit val timeout = Timeout(5 seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(webhookToSQSService, interface = "localhost", port = 8080)

  val sqsToZendeskService = system.actorOf(Props[SQSToZendeskServiceActor], "sqs-to-zendesk")
  val iteratee = Iteratee.foreach[Seq[SQSMessage[String]]](_ foreach {
    message => {
      val m = message.body.parseJson.convertTo[NexmoMessage]
      sqsToZendeskService ? m
    } onSuccess {
      case _ => message.consume()
    }
  })
  Util.incomingQueue.batchEnumeratorWithLock(10, Config.visibilityTimeout seconds) apply iteratee

  system.scheduler.schedule(0 seconds, 1 minute, sqsToZendeskService, CheckAllMessagePartsInDB)
}

object Config {
  private val conf = ConfigFactory.load().getConfig("zendesk-sms")

  val createTables = conf.getBoolean("create-tables")

  val awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials
  val region = Regions.valueOf(conf.getString("aws-region"))
  val incomingQueueName = conf.getString("incoming-queue")
  val visibilityTimeout = conf.getInt("visibility-timeout")
  val zendeskSite = conf.getString("zendesk-site")
  val zendeskUserEmail = conf.getString("zendesk-user-email")
  val zendeskUserToken = conf.getString("zendesk-user-token")
  val zendeskPhoneDomain = conf.getString("zendesk-phone-domain")
}

object Util {
  val db = Database.forConfig("database")
  val sqs = SimpleSQSClient(Config.awsCredentials, Config.region, buffered = false)
  val incomingQueue = sqs.simple(QueueName(Config.incomingQueueName))

  val nexmoTimestampRegex = """(\d\d\d\d)-(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)""".r
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  def phoneToEmail(phone: String) = s"$phone@${Config.zendeskPhoneDomain}"
}