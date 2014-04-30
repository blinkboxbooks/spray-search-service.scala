package com.blinkboxbooks.common.spray

import akka.actor.{ Actor, Props, ActorSystem }
import spray.routing._

trait Core {
  implicit def system: ActorSystem
}

trait BootedCore extends Core {
  implicit lazy val system = ActorSystem("akka-spray")
  sys.addShutdownHook(system.shutdown())
}

