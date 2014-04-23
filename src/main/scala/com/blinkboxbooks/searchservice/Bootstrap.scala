package com.blinkboxbooks.searchservice

import akka.actor.{ Actor, Props, ActorSystem }
import spray.routing._

trait Core {
  implicit def system: ActorSystem
}

trait BootedCore extends Core {
  implicit lazy val system = ActorSystem("akka-spray")
  sys.addShutdownHook(system.shutdown())
}

object RoutedHttpService {
  def props(route: Route): Props = Props(new RoutedHttpService(route))
}

class RoutedHttpService(route: Route) extends Actor with HttpService {
  def actorRefFactory = context
  def receive = runRoute(route)
}
