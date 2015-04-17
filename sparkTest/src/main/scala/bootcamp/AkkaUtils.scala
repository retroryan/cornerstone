package bootcamp

import akka.actor.{ActorSystem, ExtendedActorSystem}
import com.typesafe.config.ConfigFactory
import org.apache.spark._

import scala.collection.JavaConversions.mapAsJavaMap
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 *
 * copied from:
 * https://github.com/apache/spark/blob/master/core/src/main/scala/org/apache/spark/util/AkkaUtils.scala
 *
 *
 * Various utility classes for working with Akka.
 */
object AkkaUtils extends Logging {

  /**
   * Creates an ActorSystem ready for remoting, with various Spark features. Returns both the
   * ActorSystem itself and its port (which is hard to get from Akka).
   *
   * Note: the `name` parameter is important, as even if a client sends a message to right
   * host + port, if the system name is incorrect, Akka will drop the message.
   *
   * If indestructible is set to true, the Actor System will continue running in the event
   * of a fatal exception.
   */
  def createActorSystem(
                         name: String,
                         host: String,
                         port: Int,
                         conf: SparkConf): (ActorSystem, Int) = {

      doCreateActorSystem(name, host, port, conf)
  }

  private def doCreateActorSystem(
                                   name: String,
                                   host: String,
                                   port: Int,
                                   conf: SparkConf): (ActorSystem, Int) = {

    val akkaThreads = conf.getInt("spark.akka.threads", 4)
    val akkaBatchSize = conf.getInt("spark.akka.batchSize", 15)
    val akkaTimeout = conf.getInt("spark.akka.timeout", 100)
    val akkaFrameSize = maxFrameSizeBytes(conf)
    val akkaLogLifecycleEvents = conf.getBoolean("spark.akka.logLifecycleEvents", false)
    val lifecycleEvents = if (akkaLogLifecycleEvents) "on" else "off"
    if (!akkaLogLifecycleEvents) {
      // As a workaround for Akka issue #3787, we coerce the "EndpointWriter" log to be silent.
      // See: https://www.assembla.com/spaces/akka/tickets/3787#/
      // Option(Logger.getLogger("akka.remote.EndpointWriter")).map(l => l.setLevel(Level.FATAL))
    }

    val logAkkaConfig = if (conf.getBoolean("spark.akka.logAkkaConfig", false)) "on" else "off"

    val akkaHeartBeatPauses = conf.getInt("spark.akka.heartbeat.pauses", 6000)
    val akkaFailureDetector =
      conf.getDouble("spark.akka.failure-detector.threshold", 300.0)
    val akkaHeartBeatInterval = conf.getInt("spark.akka.heartbeat.interval", 1000)

    /*
        val secretKey = securityManager.getSecretKey()
        val isAuthOn = securityManager.isAuthenticationEnabled()
        if (isAuthOn && secretKey == null) {
          throw new Exception("Secret key is null with authentication on")
        }
        val requireCookie = if (isAuthOn) "on" else "off"
        val secureCookie = if (isAuthOn) secretKey else ""
    */

    val requireCookie = false
    val secureCookie = ""

    logDebug("In createActorSystem, requireCookie is: " + requireCookie)

    val akkaConf = ConfigFactory.parseMap(conf.getAkkaConf.toMap[String, String]).withFallback(
      ConfigFactory.parseString(
        s"""
           |akka.daemonic = on
           |akka.loggers = [""akka.event.slf4j.Slf4jLogger""]
           |akka.stdout-loglevel = "ERROR"
           |akka.jvm-exit-on-fatal-error = off
           |akka.remote.require-cookie = "$requireCookie"
                                                          |akka.remote.secure-cookie = "$secureCookie"
                                                                                                       |akka.remote.transport-failure-detector.heartbeat-interval = $akkaHeartBeatInterval s
                                                                                                                                                                                            |akka.remote.transport-failure-detector.acceptable-heartbeat-pause = $akkaHeartBeatPauses s
                                                                                                                                                                                                                                                                                       |akka.remote.transport-failure-detector.threshold = $akkaFailureDetector
            |akka.actor.provider = "akka.remote.RemoteActorRefProvider"
            |akka.remote.netty.tcp.transport-class = "akka.remote.transport.netty.NettyTransport"
            |akka.remote.netty.tcp.hostname = "$host"
                                                      |akka.remote.netty.tcp.port = $port
            |akka.remote.netty.tcp.tcp-nodelay = on
            |akka.remote.netty.tcp.connection-timeout = $akkaTimeout s
                                                                      |akka.remote.netty.tcp.maximum-frame-size = ${akkaFrameSize}B
                                                                                                                                    |akka.remote.netty.tcp.execution-pool-size = $akkaThreads
            |akka.actor.default-dispatcher.throughput = $akkaBatchSize
            |akka.log-config-on-start = $logAkkaConfig
            |akka.remote.log-remote-lifecycle-events = $lifecycleEvents
            |akka.log-dead-letters = $lifecycleEvents
            |akka.log-dead-letters-during-shutdown = $lifecycleEvents
      """.stripMargin))

    val actorSystem = ActorSystem(name, akkaConf)
    val provider = actorSystem.asInstanceOf[ExtendedActorSystem].provider
    val boundPort = provider.getDefaultAddress.port.get
    (actorSystem, boundPort)
  }

  /** Returns the default Spark timeout to use for Akka ask operations. */
  def askTimeout(conf: SparkConf): FiniteDuration = {
    Duration.create(conf.getLong("spark.akka.askTimeout", 30), "seconds")
  }

  /** Returns the default Spark timeout to use for Akka remote actor lookup. */
  def lookupTimeout(conf: SparkConf): FiniteDuration = {
    Duration.create(conf.getLong("spark.akka.lookupTimeout", 30), "seconds")
  }

  /** Returns the configured max frame size for Akka messages in bytes. */
  def maxFrameSizeBytes(conf: SparkConf): Int = {
    conf.getInt("spark.akka.frameSize", 10) * 1024 * 1024
  }

  /** Space reserved for extra data in an Akka message besides serialized task or task result. */
  val reservedSizeBytes = 200 * 1024


}
