package net.tomasherman.replayvault.client.logic

import akka.actor.Actor
import com.codahale.logula.Logging
import java.io._
import java.net.URL
import java.util.Date
import net.tomasherman.replayvault.client._
import org.scala_tools.subcut.inject.{BindingModule, Injectable}
import scala.io.Source
import ReplayState._
import scalax.file.Path
import scalax.io.Input
import scalax.io.Output
import scalax.io._


class ModelActor(implicit val bindingModule: BindingModule) extends Actor with ModelActorFunctionality with Injectable {
  
  val fileCfg = inject[FileConfig]
  val path = Path(fileCfg.cacheFile)
  val io = new FSModelIO(path)
  override def preStart() { 
    loadCache()
  }

  def receive = {
    case GetModel() => sender ! model
    case GetTimes() => sender ! uploadedTime
    case GetHashes() => sender ! cache
    case UpdateModel(model,newState) => updateModel(model,newState); saveCache()
    case SaveCache() => saveCache()
    case GetURL(x) => sender ! urls.get(x)
  }
}

trait ModelActorFunctionality extends Logging {
  val io: ModelIO

  var model = Map.empty[ReplayModel,ReplayState]
  var cache = Set.empty[String]
  var urls = Map.empty[String,URL]
  var uploadedTime = Map.empty[String, Date]
  2
  
  def updateModel(replayModel: ReplayModel, newState: ReplayState.ReplayState) {
    if(model.get(replayModel) != Some(newState)) {
      model = model + (replayModel -> newState)
      if(newState == UPLOADED) {
        updateCache(replayModel.hash)
        replayModel.url map { x:URL => urls = urls + (replayModel.hash -> x)}
        updateUploadTime(replayModel.hash)
        log.debug("Model updated: %s -> %s", replayModel, newState)
      }
    }
  }

  def updateCache(hash: String) { cache = cache + hash }

  def updateUploadTime(hash: String) {
    uploadedTime = uploadedTime + (hash -> uploadedTime.get(hash).getOrElse(new Date()))
  }

    
  def loadCache() {
    io.loadCache() foreach { x =>
      cache = cache + x._1
      uploadedTime = uploadedTime + (x._1 -> x._2)
      x._3 map { u:URL => urls = urls + (x._1 -> u) }
    }
  }
  
  def saveCache() {
    val toBeSaved = cache ++ model.filter(_._2 == UPLOADED).map(_._1.hash)
    val data = toBeSaved map { x => (x,uploadedTime(x),urls.get(x)) }
    io.saveCache(data)
  }
}

trait ModelIO {
  def loadCache(): Set[(String,Date,Option[URL])]
  def saveCache(data: Set[(String,Date,Option[URL])])
}

class FSModelIO(val file: Path) extends ModelIO with Logging {
  def mkDate(str: String) = new Date(str.toLong)
  def parseLine(line: String) = {
    try {
      line.split('|') match {
        case Array(hash,date) => Some((hash,mkDate(date),None))
        case Array(hash,date,url) => Some((hash,mkDate(date),Some(new URL(url))))
        case _ => throw new Exception("Malformed line: %s" format (line))
      }
    } catch {
      case x:Exception => {
        log.debug("Error while reading cache: %s. Ignoring line." format x.getMessage())
        None
      }
    }
  }
  def formatLine(data: (String,Date,Option[URL])) = {
    data._3 match {
      case None => "%s|%s" format (data._1, data._2.getTime)
      case Some(url) => "%s|%s|%s" format (data._1,data._2.getTime,url)
    }
  }

  def loadCache() = {
    var set = Set.empty[(String,Date,Option[URL])]
    file.lines() foreach { x =>
      for (l <- parseLine(x)){
        set = set + l
      }
    }
    set
  }
  
  def saveCache(data: Set[(String,Date,Option[URL])]) {
    val strs = data.view.map (formatLine(_))
    file.writeStrings(strs,"\n")
  }
}
