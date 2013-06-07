package net.tomasherman.replayvault.client.gui

import akka.dispatch.Await
import akka.pattern.ask
import java.io.File
import net.tomasherman.replayvault.client.logic._
import org.scala_tools.subcut.inject.BindingModule
import org.streum.configrity.Configuration
import org.streum.configrity.converter.Extra._
import scala.swing._

class SourcesForm(implicit val bindingModule: BindingModule) extends UniformGridBagPanel with InjectedAkkaSupport with SavableComponent {
  val configActor = lookupActor("service")
  
  val cfg = Await.result(configActor ? GetConfig(), timeout.duration).asInstanceOf[Configuration]
  val dirs = cfg.get[List[File]](ConfigKeys.directories)
  val list = new ListView(dirs.getOrElse(List.empty[File]))
  
  val panel = this
  val button = new SelfButton("Add folder", { () => 
    val f = new FileChooser()
    f.fileSelectionMode = FileChooser.SelectionMode.DirectoriesOnly
    f.showDialog(this, "Choose") match {
      case FileChooser.Result.Approve => {
        list.listData = list.listData :+ f.selectedFile
      }
      case _ =>
    }
  })

  def custom(c: Constraints) {
    c.weightx = 1.0
    c.grid = (1,2)
    add(button,c)
    c.grid = (1,3)
    c.weighty = 1.0
    add(new ScrollPane(list),c)
  }
  buildPanel
  def save() {
    (configActor ? GetConfig()).mapTo[Configuration] map { x: Configuration =>
      configActor ! UpdateListKey(ConfigKeys.directories, list.listData.toSet.toList)
    }
  }
}
