package net.tomasherman.replayvault.client.gui
import java.awt.Dimension
import java.awt.GridBagConstraints
import org.scala_tools.subcut.inject.BindingModule
import scala.swing._


class TabConfig(implicit val bindingModule: BindingModule) extends Dialog with InjectedAkkaSupport {

  val dialog = this
  title = "Preferences"
  
  
  class TabContent extends GridBagPanel {
    val pageSeq: Seq[(String,String,Component with SavableComponent)] = Vector(
      ("Credentials","nizzle",new CredentialsForm),
      ("Sources", "sources", new SourcesForm),
      ("Guts", "guts", new GutsConfigForm)
    )

    val tabz = new TabbedPane {
      import TabbedPane.Page
      pageSeq foreach { x =>
      pages += new Page(x._1,x._3,x._2)
    }}
    
    add(tabz,{
      val c = new Constraints()
      c.grid = (1,1)
      c.fill = GridBagPanel.Fill.Both
      c.weightx = 1.0
      c.weighty = 1.0
      c.gridwidth = 2
      c.gridheight = 1
      c.insets = new Insets(0,0,15,0)
      c
    })

    add(new SelfButton("Save", {() =>
      pageSeq foreach(_._3.save)
      dialog.dispose()
    }), buttonConstraints(1,2,GridBagPanel.Anchor.LastLineStart))
    add(new SelfButton("Exit",{ () => dialog.dispose() }), buttonConstraints(2,2,GridBagPanel.Anchor.LastLineEnd))
  
    def buttonConstraints(x: Int, y: Int, anchor: GridBagPanel.Anchor.Value) = {
      val c = new Constraints()
      c.grid = (x,y)
      c.anchor = anchor
      c.weightx = 1.0
      c
    }
  }

  val content = new TabContent
  contents = new GridBagPanel {
    add(content, {
      val c = new Constraints()
      c.insets = new Insets(15,15,15,15)
      c.fill = GridBagPanel.Fill.Both
      c.gridwidth = 1
      c.gridheight = 1
      c.weightx = 1.0
      c.weighty = 1.0
      c.grid = (1,1)
      c
    })
  }
  size = new Dimension(480,320)
  resizable = false
}
