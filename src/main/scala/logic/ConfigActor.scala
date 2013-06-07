package net.tomasherman.replayvault.client.logic

import akka.actor.Actor
import akka.actor.ActorRef
import com.codahale.logula.Logging
import java.net.URL
import net.tomasherman.replayvault.client.FileConfig
import org.scala_tools.subcut.inject.BindingModule
import org.scala_tools.subcut.inject.Injectable
import org.streum.configrity.Configuration
import scala.io.Source
import scalax.file._
import scalax.io.Output
import scalax.io.Resource
import scalax.io._
import scalax.io.StandardOpenOption._
import scalax.io.managed.OutputStreamResource

class ConfigActor(implicit val bindingModule: BindingModule) extends Actor with Injectable with ConfigActorFunctionality {
  val defaultConfig = Configuration().set(ConfigKeys.service, new URL("http://ggtracker.com/api/upload")).set(ConfigKeys.rescanRate,10)
  val fcfg = inject[FileConfig]
  var config = {
    val cfg = loadConfiguration(Source.fromFile(fcfg.cfgFile)) include defaultConfig
    log.info("Configuration loaded: %s", cfg)
    cfg
  }
  def outputFile:Path  = Path(fcfg.cfgFile)
  var subscribed = Set.empty[ActorRef]
  def receive = {
    case _:GetConfig => sender ! config
    case Subscribe() => {
      log.debug("%s subscribed for config updates", sender)
      subscribed = subscribed + sender;
      sender ! ConfigPush(config)
    }
    case x: UpdateKey => {
      updateAndPush(config.set(x.key,x.value))
      persistConfiguration
    }
    case x: UpdateListKey => {
      updateAndPush(config.set(x.key,x.value))
      persistConfiguration
    }
    case x: DeleteKey => {
      updateConfig(config.clear(x.key))
      persistConfiguration
    }
  }

  def updateAndPush(cfg: Configuration) {
    updateConfig(cfg)
    subscribed foreach { _ ! ConfigPush(config) }
  }
}

trait ConfigActorFunctionality extends Logging {
  var config: Configuration
  def outputFile: Path

  
  def persistConfiguration() { outputFile.outputStream(WriteTruncate:_*).write(config.format()) }
  def loadConfiguration(src: Source) = {
    Configuration.load(src)
  }
  def updateConfig(cfg: Configuration) {
    log.info("New configuration saved: %s", cfg)
    config = cfg
  } 
  
}
