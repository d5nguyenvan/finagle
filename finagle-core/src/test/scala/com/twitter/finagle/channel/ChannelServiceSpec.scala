package com.twitter.finagle.channel

import scala.collection.JavaConversions._

import org.specs.Specification
import org.specs.mock.Mockito
import org.mockito.{Matchers, ArgumentCaptor}

import org.jboss.netty.channel._

import com.twitter.util.{Promise, Return, Throw}

import com.twitter.finagle._

object ChannelServiceSpec extends Specification with Mockito {
  "ChannelService" should {
    val pipeline = new DefaultChannelPipeline
    val channel = mock[Channel]
    val sink = mock[ChannelSink]
    channel.getPipeline returns pipeline
    channel.isOpen returns true
    pipeline.attach(channel, sink)

    "installs channel handler" in {
      pipeline.toMap.keySet must haveSize(0)
      new ChannelService[Any, Any](channel)
      pipeline.toMap.keySet must haveSize(1)
    }

    "write requests to the underlying channel" in {
      val service = new ChannelService[String, String](channel)
      val future = service("hello")
      val eventCaptor = ArgumentCaptor.forClass(classOf[ChannelEvent])
      there was one(sink).eventSunk(Matchers.eq(pipeline), eventCaptor.capture)
      
      eventCaptor.getValue must haveClass[DownstreamMessageEvent]
      eventCaptor.getValue.asInstanceOf[DownstreamMessageEvent].getMessage must be_==("hello")
    }

    "receive replies" in {
      val service = new ChannelService[String, String](channel)
      val future = service("hello")
      there was one(sink).eventSunk(Matchers.eq(pipeline), Matchers.any[ChannelEvent])

      val handler = pipeline.getLast.asInstanceOf[ChannelUpstreamHandler]
      val context = mock[ChannelHandlerContext]
      val event = mock[MessageEvent]
      event.getMessage returns "olleh"
      future.isDefined must beFalse
      service.isAvailable must beTrue

      "on success" in {
        handler.handleUpstream(context, event)
        future.isDefined must beTrue
        future() must be_==("olleh")
        service.isAvailable must beTrue
      }

      "on casting error" in {
        event.getMessage returns mock[Object]  // bad type
        handler.handleUpstream(context, event)
        future.isDefined must beTrue
        future() must throwA[ClassCastException]
        // service.isAvailable must beFalse
      }

      "on channel exception" in {
        val exceptionEvent = mock[ExceptionEvent]
        exceptionEvent.getCause returns new Exception("weird")
        handler.handleUpstream(context, exceptionEvent)
        future.isDefined must beTrue
        future() must throwA(new UnknownChannelException(new Exception("weird")))
        service.isAvailable must beFalse
      }

      "on channel close" in {
        val stateEvent = mock[ChannelStateEvent]
        stateEvent.getState returns ChannelState.OPEN
        stateEvent.getValue returns java.lang.Boolean.FALSE
        handler.handleUpstream(context, stateEvent)

        future.isDefined must beTrue
        future() must throwA[ChannelClosedException]
        service.isAvailable must beFalse
      }
    }

    "without a request" in {
      val service = new ChannelService[String, String](channel)
      service.isAvailable must beTrue

      "any response is considered spurious" in {
        val handler = pipeline.getLast.asInstanceOf[ChannelUpstreamHandler]
        val context = mock[ChannelHandlerContext]
        val event = mock[MessageEvent]
        event.getMessage returns "hello"
        handler.handleUpstream(context, event)
        service.isAvailable must beFalse
      }
    }

    "freak out on concurrent requests" in {
      val service = new ChannelService[Any, Any](channel)
      val f0 = service("hey")
      f0.isDefined must beFalse
      val f1 = service("there")
      f1.isDefined must beTrue
      f1() must throwA[TooManyConcurrentRequestsException]
    }
  }
}
