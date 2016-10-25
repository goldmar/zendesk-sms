zendesk-sms
===========

This project integrates [Zendesk](https://www.zendesk.com/), an issue tracking software, with [Nexmo](https://www.nexmo.com/), an SMS API. I wrote it back in April 2015 and it was inspired by [services such as Magic and Go Butler](https://techcrunch.com/2015/04/01/go-butler/). By now it is probably obsolete since [Zendesk is about to release its own support for SMS](https://www.zendesk.com/mobile-solutions/sms-customer-service/).

The project is written in Scala and makes use of the [akka](http://akka.io/) library and [AWS SQS](https://aws.amazon.com/sqs/).

The concept was follows:

A phone number is registered at Nexmo. When a user writes an SMS to this phone number, Nexmo forwards the phone message to the webhook configured in its interface. This webhook is exposed in this project. When a the webhook receives a Nexmo message, it puts it into an AWS SQS queue. This queue is regularly polled and new messages are processed depending on their context: If this is the first message from this phone number, then a new user in Zendesk is created and a new issue is created containing the content of the SMS. If a corresponding user already exists, a search for issues from this user is performed. In case there are no open issues, a new issue is created. If there is at least one open issue, then a new comment to the most recent open issue is appended.

Usage
-----

This project is licensed under the terms of the Apache License, Version 2.0.
