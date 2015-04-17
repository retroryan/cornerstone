package bootcamp

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 *
 * copied from:
 * https://github.com/apache/spark/blob/master/examples/src/main/scala/org/apache/spark/examples/streaming/ActorWordCount.scala
 */

import scala.collection.immutable.HashSet
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.Random

import akka.actor.{Actor, ActorRef, Props, actorRef2Scala}


import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.StreamingContext.toPairDStreamFunctions
import org.apache.spark.streaming.receiver.ActorHelper

import scala.concurrent.duration._

case class SubscribeReceiver(receiverActor: ActorRef)

case class UnsubscribeReceiver(receiverActor: ActorRef)

case object SendNextMessages

/**
 * Sends the random content to every receiver subscribed with 1/2
 * second delay.
 */
class FeederActor extends Actor {

  println("FeederActor started as:" + context.self)

  val rand = new Random()
  var receivers: HashSet[ActorRef] = new HashSet[ActorRef]()

  val strings: Array[String] = ("Apache Spark is a fast and general-purpose cluster computing system").split(" ").toArray[String]

  def makeMessage(): String = {
    val x = rand.nextInt(strings.size)
    strings(x) + strings((strings.size - 1) - x)
  }

  implicit val executionContext = context.system.dispatcher
  val stockTick = context.system.scheduler.schedule(Duration.Zero, 500.millis, self, SendNextMessages)

  def receive: Receive = {

    case SubscribeReceiver(receiverActor: ActorRef) =>
      println("received subscribe from %s".format(receiverActor.toString))
      receivers = receivers + receiverActor //LinkedList(receiverActor) ++ receivers

    case UnsubscribeReceiver(receiverActor: ActorRef) =>
      println("received unsubscribe from %s".format(receiverActor.toString))
      receivers = receivers - receiverActor //receivers.dropWhile(x => x eq receiverActor)

    case SendNextMessages => {
      receivers.foreach(_ ! makeMessage)
    }
  }
}

/**
 * A sample actor as receiver, is also simplest. This receiver actor
 * goes and subscribe to a typical publisher/feeder actor and receives
 * data.
 *
 * @see [[actors.FeederActor]]
 */
class SampleActorReceiver[T: ClassTag](urlOfPublisher: String)
  extends Actor with ActorHelper {

  println(s"Started SampleActorReceiver")

  lazy private val remotePublisher = context.actorSelection(urlOfPublisher)

  override def preStart() = {
    println(s"remotePublisher = $remotePublisher")
    remotePublisher ! SubscribeReceiver(context.self)
  }

  def receive = {
    case msg => {
      println(s"SampleActorReceiver $msg")
      store(msg.asInstanceOf[T])
    }
  }

  override def postStop() = remotePublisher ! UnsubscribeReceiver(context.self)

}

/**
 * A sample feeder actor
 *
 * Usage: FeederActor <hostname> <port>
 * <hostname> and <port> describe the AkkaSystem that Spark Sample feeder would start on.
 */
object FeederActor {

  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println(
        "Usage: FeederActor <hostname> <port>\n"
      )
      System.exit(1)
    }
    val Seq(host, port) = args.toSeq

    val conf = new SparkConf
    val actorSystem = AkkaUtils.createActorSystem("test", host, port.toInt, conf = conf)._1
    val feeder = actorSystem.actorOf(Props[FeederActor], "FeederActor")

    println("Feeder started as:" + feeder)

    actorSystem.awaitTermination()
  }
}

/**
 * A sample word count program demonstrating the use of plugging in
 * Actor as Receiver
 * Usage: ActorWordCount <hostname> <port>
 * <hostname> and <port> describe the AkkaSystem that Spark Sample feeder is running on.
 *
 * To run this example locally, you may run Feeder Actor as
 * `$ bin/run-example org.apache.spark.examples.streaming.FeederActor 127.0.1.1 9999`
 * and then run the example
 * `$ bin/run-example org.apache.spark.examples.streaming.ActorWordCount 127.0.1.1 9999`
 */
object ActorWordCount {
  def main(args: Array[String]) {
    if (args.length < 2) {
      System.err.println(
        "Usage: ActorWordCount <hostname> <port>")
      System.exit(1)
    }

    //StreamingExamples.setStreamingLogLevels()

    //172.16.131.133

    val Seq(host, port) = args.toSeq
    val sparkConf = new SparkConf()
      .setAppName("ActorWordCount")
      .set("spark.cassandra.connection.host", "10.0.0.26")
      .setJars(Array("target/scala-2.10/dse_spark_streaming_examples-assembly-0.2.0.jar"))
      .setMaster("spark://10.0.0.26:7077")

    // Create the context and set the batch size
    val ssc = new StreamingContext(sparkConf, Seconds(2))
    ssc.checkpoint("/tmp/spark")

    /*
     * Following is the use of actorStream to plug in custom actor as receiver
     *
     * An important point to note:
     * Since Actor may exist outside the spark framework, It is thus user's responsibility
     * to ensure the type safety, i.e type of data received and InputDstream
     * should be same.
     *
     * For example: Both actorStream and SampleActorReceiver are parameterized
     * to same type to ensure type safety.
     */

    val lines = ssc.actorStream[String](
      Props(new SampleActorReceiver[String]("akka.tcp://test@%s:%s/user/FeederActor".format(
        host, port.toInt))), "SampleReceiver")

    // compute wordcount
    val wordMap = lines.flatMap(_.split("\\s+")).map(x => (x, 1)).reduceByKey(_ + _)
    wordMap.print()

    val windowedWordCounts = wordMap
      .reduceByKeyAndWindow( (a: Int, b: Int) => (a + b) , Seconds(30), Seconds(10))
      .map{ case (str:String, c:Int) => ("TOT: " + str, c) }
      .print()

    wordMap.countByWindow(Seconds(30), Seconds(10))
      .map { count => s"Window Count: $count"}
      .print()

    wordMap.countByValueAndWindow(Seconds(30), Seconds(10))
      .map { case (str:(String, Int), count:Long) => s"Value Window Count for str: ${str._1} with count: $count"}
      .print()

    wordMap.checkpoint(Seconds(60))

    ssc.start()
    ssc.awaitTermination()
  }
}