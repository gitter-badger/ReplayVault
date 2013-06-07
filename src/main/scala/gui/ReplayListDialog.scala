package net.tomasherman.replayvault.client.gui

import ReplayList._
import akka.actor.{Actor, PoisonPill, Props}
import akka.dispatch.Await
import akka.pattern._
import akka.util.duration._
import com.codahale.logula.Logging
import java.awt.Color
import java.io.File
import java.net.URL
import java.util.Date
import javax.swing.{DefaultListCellRenderer, JList}
import net.tomasherman.replayvault.client.logic._
import net.tomasherman.replayvault.client.logic.ReplayState._
import org.scala_tools.subcut.inject.BindingModule
import scala.swing._
import scala.swing.ListView._
import scala.swing.event._

object ReplayList {
  type ReplayPair = Tuple2[ReplayModel, ReplayState]
}

class ReplayList(implicit val bindingModule: BindingModule) extends Dialog with InjectedAkkaSupport with Logging {

  title = "Replay view"
  val refresh = system.scheduler.schedule(0 seconds, 10 seconds, new Runnable {
    def run() { updateView() }
  })

  val model = lookupActor("service")
  val replayList = this
  val color = (data: (ReplayModel, ReplayState)) => data match {
    case (x, UPLOADED) => new Color(79, 240, 109)
    case (x, FAILED) => Color.magenta
  }
  val label = new Label("")
  val list = new ColorfulListView[ReplayPair](Seq.empty, color, (_._1.file.getName))

  var config: ReplayListViewConfiguration = DefaultConfig

  def updateView() {
    for(
      data  <- (model ? GetModel()).mapTo[Map[ReplayModel, ReplayState]];
      times <-(model ? GetTimes()).mapTo[Map[String, Date]]
    ) {
      val d = data.view.toIndexedSeq.sortWith({ (p1, p2) =>
        config.sort((p1, times.get(p1._1.hash)), (p2, times.get(p2._1.hash)))
      })
      val filteredD = (d /: config.filter) { (res, f) => res.filter(f) }
      list.listData = filteredD
      label.text = "Number of replays: %s" format filteredD.size
      pack()
    }
  }

  private class Content extends GridBagPanel {
    val c = new Constraints()
    c.fill = GridBagPanel.Fill.Both
    c.weightx = 1
    c.grid = (1,1)
    c.gridwidth = 2
    add(new SelfButton("View options", { () =>
      (new ReplayListViewOptionDialog(replayList)).visible = true
    }),c)
    c.weighty = 1
    c.grid = (1,2)
    add(new ScrollPane { contents = list },c)
    c.weighty = 0
    c.grid = (1,3)
    add(label,c)
    c.grid = (1,4)
    c.gridwidth = 1
    c.weightx = 0
    add(new Label("Replay url:"),c)
    c.grid = (2,4)
    c.weightx = 1
    add(new TextField() {
      listenTo(list.selection)

      reactions += {
        case x: SelectionChanged => {
          val o = list.selection.items.headOption
          val a = o.map(_._1.hash)
          a map { x: String => updateThyself(x) }
        }
      }
      val me = this
      def updateThyself(hash: String) {
        val url = Await.result(model ? GetURL(hash), timeout.duration).asInstanceOf[Option[URL]]
        text = url.getOrElse("URL NOT FOUND").toString
      }
    },c)
  }
  contents = new Content

  updateView()

  override def closeOperation() {
    refresh.cancel
  }
}

case class ReplayListViewConfiguration(
  sort: ((ReplayPair, Option[Date]), (ReplayPair, Option[Date])) => Boolean,
  filter: Seq[(ReplayPair) => Boolean])

object DefaultConfig extends ReplayListViewConfiguration(
  ComparingFunctions.cmpByDateAsc _,
  Seq())

object ComparingFunctions {
  import ReplayListSortChoice._
  import GenericSortChoice._

  type Pair = (ReplayPair, Option[Date])

  def cmpByNameDes(p1: Pair, p2: Pair) = !cmpByNameAsc(p1, p2)
  def cmpByDateDes(p1: Pair, p2: Pair) = !cmpByDateAsc(p1, p2)
  def cmpByNameAsc(p1: Pair, p2: Pair) = p1._1._1.file.getName.toLowerCase.compareTo(p2._1._1.file.getName.toLowerCase) < 0
  def cmpByDateAsc(p1: Pair, p2: Pair) = (p1._2, p2._2) match {
    case (None, None) => false
    case (None, _) => true
    case (_, None) => false
    case (Some(x), Some(y)) => (x.getTime - y.getTime) < 0
  }

  def figureCmpFunction(by: ReplayListSortChoice, ascDesc: GenericSortChoice) = {
    (by, ascDesc) match {
      case (DATE, DESC) => cmpByDateDes _
      case (DATE, ASC) => cmpByDateAsc _
      case (NAME, DESC) => cmpByNameDes _
      case (NAME, ASC) => cmpByNameAsc _
    }
  }

}

object ReplayListSortChoice extends Enumeration {
  type ReplayListSortChoice = Value
  val DATE = Value("Upload date")
  val NAME = Value("Replay name")
}

object GenericSortChoice extends Enumeration {
  type GenericSortChoice = Value
  val ASC = Value("Ascending")
  val DESC = Value("Descending")
}

class ReplayListViewOptionDialog(val list: ReplayList) extends Dialog {
  val dialog = this
  private class Content extends GridPanel(5, 2) {

    implicit val to = this
    val sortBy = new ComboBox(ReplayListSortChoice.values.toSeq)
    val ascDesc = new ComboBox(GenericSortChoice.values.toSeq)
    val filterState = new ComboBox(ReplayState.values.toSeq)
    val filterName = new TextField(".*")

    labeledComponentTo("Sort by: ", sortBy)
    labeledComponentTo("Asc/Desc: ", ascDesc)
    labeledComponentTo("Replay state: ", filterState)
    labeledComponentTo("Name filter (java regex): ", filterName)

    contents += new SelfButton("... and thats how it's going to be!", applyChangesCallback _)

    contents += new SelfButton("Exit", () => { dialog.dispose })

    def buildStateFilter(choice: ReplayState) = {
      choice match {
        case NOTINTERESTING => { (_: ReplayPair) => true }
        case x => { (pair: ReplayPair) => pair._2 == x }
      }
    }
    def buildNameFilter(choice: String) = {
      choice match {
        case "" => { (_: ReplayPair) => true }
        case x: String => {
          { (pair: ReplayPair) => x.r.pattern.matcher(pair._1.file.getName).matches }
        }
      }
    }
    def applyChangesCallback = {
      val selectedBy = sortBy.selection.item
      val selectedAD = ascDesc.selection.item
      import ComparingFunctions._

      val sortF = figureCmpFunction(selectedBy, selectedAD)

      val state = buildStateFilter(filterState.selection.item)

      val name = buildNameFilter(filterName.text.trim)

      list.config = new ReplayListViewConfiguration(sortF, Seq(state, name))
      list.updateView
    }

  }

  contents = new Content

}

class ColorfulListView[A](data: Seq[A], color: (A) => Color, val map: (A) => Any)
  extends ListView[A](data) with CustomRenderedListView[A] {
  def colorFor(item: A) = color(item)
}

trait CustomRenderedListView[A] {
  self: ListView[A] =>
  def map: (A) => Any
  def colorFor(item: A): Color
  val cellRenderer = new DefaultListCellRenderer {
    override def getListCellRendererComponent(list: JList, value: Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean) = {
      val c = super.getListCellRendererComponent(list, map(value.asInstanceOf[A]), index, isSelected, cellHasFocus)
      c.setBackground(colorFor(value.asInstanceOf[A]))
      c
    }
  }
  peer.setCellRenderer(cellRenderer)
}
