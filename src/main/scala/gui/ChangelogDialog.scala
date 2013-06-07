package net.tomasherman.replayvault.client.gui
import net.tomasherman.replayvault.client.FileConfig
import org.scala_tools.subcut.inject.BindingModule
import org.scala_tools.subcut.inject.Injectable

import scala.swing._
import scalax.file._

class ChangelogDialog(implicit val bindingModule: BindingModule) extends Dialog with Injectable {
  val files = inject[FileConfig]

  val path = Path(files.changelog)
  val content = if(!path.exists) "Changelog file not found! That is weird!" else {
    path.lines(includeTerminator = true).mkString
  }

  class Content extends BoxPanel(Orientation.Vertical) {
    contents += new ScrollPane {
      contents = new TextArea(content, 40, 60)
    }
  }
  
  contents = new Content
}

