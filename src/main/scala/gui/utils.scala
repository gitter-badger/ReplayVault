package net.tomasherman.replayvault.client.gui

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.util.duration._
import scala.swing._
import org.scala_tools.subcut.inject._
import scala.swing.event.ButtonClicked

trait InjectedAkkaSupport extends Injectable {
  val bindingModule: BindingModule
  
  val system = inject[ActorSystem]
  implicit val timeout = Timeout(1 second)

  def lookupActor(name: String) = system.actorFor("user/%s" format name)
}

class SelfButton(name:String, onClick: () => Unit) extends Button(name) {
  listenTo(this)
  reactions += {
    case ButtonClicked(b) if b == this => onClick()
  }
}

object labeledComponentTo {
  def apply(label: String, comp: Component)(implicit to: GridPanel) {
    val l = new Label(label)
    to.contents += l
    to.contents += comp
  }
}

object fillSpace {
  def apply(comp: Component) = {
    new BorderPanel {
      add(comp,BorderPanel.Position.Center)
    }
  }
}

trait SavableComponent {
  self: Component =>

  def save(): Unit
}

trait UniformGridBagPanel extends GridBagPanel {

  def buildPanel {
    val c = new Constraints()
    c.fill = GridBagPanel.Fill.Both
    c.grid = (1,1)
      c.insets = new Insets(10,5,0,5)
    add(new Label(),c)
    c.insets = new Insets(0,5,5,5)

    custom(c)

    
  }

  def buildFooter(c: Constraints) {
    c.grid = (2,4)
    c.weighty = 1
    add(new Label(),c)
  }
  def custom(c: Constraints): Unit
}

