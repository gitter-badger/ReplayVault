package net.tomasherman.replayvault.client

import Utils._
import akka.actor._
import com.codahale.logula.Logging
import java.awt.{Image, MenuItem, PopupMenu, Toolkit, TrayIcon}
import java.awt.event.{ActionEvent, ActionListener}
import java.io._
import javax.swing.UIManager
import org.apache.log4j.Level
import scala.swing.Dialog
import scala.swing.{MainFrame, SimpleSwingApplication}
import scalax.io.Resource
import logic._
import akka.util.duration._


object Main extends App with Logging {

  val files = processArgs(args.toList)
  checkFiles(files)
  setupLogging(files.logFile.getCanonicalPath)
  val f = new File("/home/arg/workspace/replayvault-client/testdata/sc2/Accounts/662168/2-S2-1-821040/Replays")
  val dcfg = createDC(f,"lolzor")

  val system = ActorSystem("ReplayVaultUploader")
  implicit val module = new DependencyModule(files,dcfg,system)
  setupActors
 

  log.warn("Starting shit up!")
  

  setupGui


  def setupGui = {

    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    
    implicit def f2listener(f: (ActionEvent) => Unit) = new ActionListener {
      def actionPerformed(e:ActionEvent) { f(e) }
    }

    def buildItem(text: String, action: (ActionEvent) => Unit) = {
      val item = new MenuItem(text)
      item.addActionListener(action)
      item
    }

    
  
    def showDialogListener(d: () => Dialog) = { _:ActionEvent => d().visible = true}
    
    val tray = java.awt.SystemTray.getSystemTray()
    val img = Resource.fromInputStream(getClass.getResourceAsStream("/icon.jpg"))
    val icon = new java.awt.TrayIcon(Toolkit.getDefaultToolkit().createImage(img.byteArray),"ReplayVault - so alpha that it doesn't even have version number :)")

    
    val menu = new PopupMenu()

    icon.setPopupMenu(menu)

    menu.add(buildItem("Exit",{ _ => shutdownGracefully(icon,system)}))
    menu.add(buildItem("Preferences", showDialogListener({ () => new gui.TabConfig})))
    menu.add(buildItem("Replay view", showDialogListener({ () => new gui.ReplayList})))
    menu.add(buildItem("Changelog view", showDialogListener({ () => new gui.ChangelogDialog})))
    
    tray.add(icon);
    Thread.sleep(10000)
  }

  
  
  def setupActors = {

    val service = system.actorOf(Props(new ScanningService),"service")
    service ! Rescan()
    system
  }

  
  
  def checkFiles(files: FileConfig) = {
    files.appRoot existsOrFail()
    files.logFile existsOrCreate()
    files.cacheFile existsOrCreate() 
    files.cfgFile existsOrCreate()
    files
  }

  def processArgs(args: List[String]):FileConfig = {
    args match {
      case appRoot :: Nil => {
        new FileConfig(new File(appRoot))
      }
      case _ => throw new Exception("Requires root!")
    }
  } 

  def createDC(accountRoot: File, login: String): DynamicConfig=
    new DynamicConfig { 
      def scanDirs = getReplayFolders(accountRoot)
      def accountName = login
    }


  def getReplayFolders(root: File) = {
    val folders = Set('Campaign, 'Challenge, 'Multiplayer, 'VersusAI)
    folders map { a => root / a.name existsOrFail()}
  }

  def setupLogging(logFile: String) { 
    Logging.configure { log =>
      log.registerWithJMX = false
      log.level = Level.DEBUG

      log.loggers("org.apache.http.wire") = Level.OFF
      log.loggers("org.apache.http.client.protocol.RequestAddCookies") = Level.OFF
      log.loggers("org.apache.http.impl.conn.SingleClientConnManager") = Level.OFF
      log.loggers("org.apache.http.impl.conn.DefaultClientConnection") = Level.OFF
      log.loggers("org.apache.http.headers") = Level.OFF
      log.loggers("dispatch.ConfiguredHttpClient") = Level.OFF
      log.loggers("org.apache.http.client.protocol.RequestAuthCache") = Level.OFF
      log.loggers("org.apache.http.client.protocol.ResponseProcessCookies") = Level.OFF

  
      log.console.enabled = true
      log.console.threshold = Level.DEBUG
      
      log.file.enabled = true
      log.file.filename = logFile
      log.file.maxSize = 10 * 1024 // KB
      log.file.retainedFiles = 5 // keep five old logs around
    } 
  }

  def shutdownGracefully(icon: TrayIcon, system: ActorSystem) = {
    log.info("Shutting down gracefully")
    java.awt.SystemTray.getSystemTray.remove(icon)
    log.info("System tray removed")
    system.shutdown()
    log.info("Actor system shut down")
    System.exit(0)
  }
}

