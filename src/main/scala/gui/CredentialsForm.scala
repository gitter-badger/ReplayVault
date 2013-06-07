package net.tomasherman.replayvault.client.gui

import akka.actor.ActorSystem
import akka.dispatch.Await
import akka.pattern._
import akka.util.Timeout
import akka.util.duration._
import net.tomasherman.replayvault.client.logic._
import org.scala_tools.subcut.inject.{BindingModule, Injectable}
import org.streum.configrity._
import scala.swing._
import scala.swing.event.ButtonClicked

class CredentialsForm(implicit val bindingModule: BindingModule) extends UniformGridBagPanel with InjectedAkkaSupport with SavableComponent {
  val configActor = lookupActor("service")
  val cfg = Await.result(configActor ? GetConfig(),timeout.duration).asInstanceOf[Configuration]
  val dialog = this   
  val uname = cfg.get[String](ConfigKeys.username)
  val pass = cfg.get[String](ConfigKeys.password)
  val nameField = new TextField(uname.getOrElse(""))
  val passField = new TextField(pass.getOrElse(""))
  def save() = {
    (configActor ? GetConfig()).mapTo[Configuration] map { x: Configuration =>
      configActor ! UpdateKey(ConfigKeys.username, nameField.text)
      configActor ! UpdateKey(ConfigKeys.password,passField.text)
    }
  }


  def custom(c:Constraints) {
    c.grid = (1,2)
    add(new Label("Login:"),c)
    c.grid = (2,2)
    c.weightx = 1
    add(nameField,c)
    c.grid = (1,3)
    c.weightx = 0
    add(new Label("Password:"),c)
    c.grid = (2,3)
    c.weightx = 1
    add(passField,c)

    buildFooter(c)
  }

  buildPanel
}




