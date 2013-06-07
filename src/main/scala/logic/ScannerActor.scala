package net.tomasherman.replayvault.client.logic

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import akka.util.duration._
import com.codahale.logula.Logging
import java.io.File
import java.security.MessageDigest
import net.tomasherman.replayvault.client._
import org.scala_tools.subcut.inject.{ BindingModule, Injectable }
import scalax.io.Resource
import scalax.io.{ Input, Resource }
import org.streum.configrity._
import org.streum.configrity.converter.Extra._

class ScannerActor(val uploader: ActorRef, val cache: ActorRef, val config: ActorRef)(implicit val bindingModule: BindingModule)
  extends Actor with ScannerFunctionality with Injectable {

  implicit val timeout = Timeout(5 seconds)
  val checker = new MD5FileInCacheChecker
  val dirScanner = new DirectoryScanner(checker,isReplay)
  val staticCfg = inject[StaticConfig]
  val dcfg = inject[DynamicConfig]
  var scanDirs = Set.empty[File]
    
  def isReplay(f: File) = staticCfg.isReplayPredicate(f)
  val uncachedCallback = (file:File, hash: String) => uploader ! Upload(ReplayModel(file,hash)())
  val cachedCallback = (file:File, hash: String) => cache ! UpdateModel(ReplayModel(file,hash)(), ReplayState.UPLOADED)

  override def preStart {
    config ! Subscribe()
  }
    
  protected def receive = {
    case Rescan() => {
      log.info("rescanning")
      for (set <- (cache ? GetHashes()).mapTo[Set[String]]) {
        scan(set, scanDirs) 
      }
    }
    case ConfigPush(cfg) => {
      updateScanDirs(cfg)
    }
  }

  def updateScanDirs(cfg: Configuration) {
    for( dirs <- cfg.get[List[File]](ConfigKeys.directories)) {
      log.info("Using new set of directories: %s", dirs)
      scanDirs = dirs.toSet
    }
  }
}

trait ScannerFunctionality extends Logging {
  def checker: ReplayInCacheChecker
  def isReplay(f: File): Boolean
  val uncachedCallback: (File, String) => Unit
  val cachedCallback: (File, String) => Unit
  def dirScanner: DirectoryScanner
  def scan(cache: Set[String], dirs: Set[File]) {
    dirs foreach (dirScanner.checkDirectory(cache, _, uncachedCallback, cachedCallback))
  }

 }

class DirectoryScanner(val checker: ReplayInCacheChecker, isReplay: File => Boolean) extends Logging {
  def checkDirectory(cache: Set[String], f: File, cachedCallback: (File,String) => Unit, uncachedCallback: (File,String) => Unit) {
    checker.findReplays(f,isReplay) foreach { f =>
      val x = checker.uncachedFile(cache, f)
      x._1 match {
        case true => cachedCallback(x._2._2,x._2._1)
        case false => uncachedCallback(x._2._2,x._2._1)
      }
    } 
  }
}

trait ReplayInCacheChecker extends ReplayFinder {
  def uncachedFile(cache: Set[String], file: File): (Boolean,(String,File))
  def resourceCreator: InputCreator
}

trait ReplayFinder {
  def findReplays(file: File, isReplay: File => Boolean) = {
    if (!file.isDirectory()) throw new Exception("File %s is not a directory!" format file)
    file.listFiles().view.filter(isReplay(_)).toSeq
  }
}

class MD5FileInCacheChecker(
  val resourceCreator: InputCreator = new InputResourceCreator) extends ReplayInCacheChecker {
  
  def calculateHash(input: Input) = {
    (MessageDigest.getInstance("MD5") /: input.bytes) {
      (h, b) =>
        h.update(b)
        h
    }.digest.map("%02X" format _).mkString
  }

  def uncachedFile(cache: Set[String], file: File) = {
    val hash = calculateHash(resourceCreator.resource(file))
    if (!cache.contains(hash)) {
      (true,(hash,file))
    } else (false,(hash,file))
  }
}

trait InputCreator {
  def resource(file: File): Input
}
  
class InputResourceCreator extends InputCreator{
  def resource(file: File) = Resource.fromFile(file)
}
 
