package net.tomasherman.replayvault.client.gui

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import akka.util.duration._
import net.tomasherman.replayvault.client.logic._
import org.scala_tools.subcut.inject.BindingModule
import org.streum.configrity._
import scala.swing._

class GutsConfigForm(implicit val bindingModule: BindingModule) extends UniformGridBagPanel with InjectedAkkaSupport with SavableComponent {
  object Services extends Enumeration {
    type Services = Value
    val GGTRACKER = Value("ggtracker")
    val SCBC = Value("sc2bc")

    val urls = Map(
      GGTRACKER -> new java.net.URL("http://ggtracker.com/api/upload"),
      SCBC -> new java.net.URL("http://sc2bc.com/upload/sc2gears")
    )

  }
  
  val service = new ComboBox(Services.values.toSeq)
  val interval = new TextField()
  implicit val panel = this
  val serviceA = lookupActor("service")

  def custom(c: Constraints) {

    c.grid = (1,2)
    add(new Label("Service API"),c)
    c.grid = (2,2)
    c.weightx = 1.0
    add(service,c)

    c.grid = (1,3)
    c.weightx = 0
    add(new Label("Rescan interval[s]"),c)
    c.grid = (2,3)
    c.weightx = 1.0
    add(interval,c)

    buildFooter(c)
  }

  buildPanel

  getConfig map { x =>
    interval.text = x[Int](ConfigKeys.rescanRate).toString
  }
  
  def getConfig = {
    implicit val timeout = Timeout(5 seconds)
    (serviceA ? GetConfig()).mapTo[Configuration]
  }

  def submit {
    val url = Services.urls(service.selection.item)
    try {
      val rescan = interval.text.toInt
      getConfig map { x =>
        serviceA ! UpdateKey(ConfigKeys.service, url)
        serviceA ! UpdateKey(ConfigKeys.rescanRate, rescan)
      }
    } catch {
      case e: Exception => println(e)
    }
  }
  
  def save() = submit
}

 
