package net.tomasherman.replayvault.client.logic

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import akka.util.duration._
import org.scala_tools.subcut.inject._
import org.streum.configrity.Configuration

class ScanningService(implicit val bindingModule: BindingModule) extends Actor with Injectable {

  val model = context.actorOf(Props(new ModelActor()),"model")
  val config = context.actorOf(Props(new ConfigActor()),"config")
  val scanner = context.actorOf(Props(new ScannerActor(http,model,config)),"scanner")
  val http = context.actorOf(Props(new HttpActor(model,config)).withDispatcher("http-priority-mbox"),"http")
//  val http = context.actorOf(Props(new HttpActor(model,config)),"http")
  
  def receive = {
    case x: GetConfig => config forward(x)
    case x: UpdateKey => config forward(x)
    case x: UpdateListKey => config forward(x)
    case x: DeleteKey => config forward(x)
    case x: GetModel => model forward(x)
    case x: GetURL => model forward(x)
    case x: GetTimes => model forward(x)
    case x: Rescan => {
      implicit val timeout = Timeout(5 seconds)
      for(
        cfg <- (config ? GetConfig()).mapTo[Configuration];
        interval <- cfg.get[Int](ConfigKeys.rescanRate)) {
          context.system.scheduler.scheduleOnce(interval seconds,self, Rescan())
        }
      scanner ! Rescan()
    }

  }

  
}
