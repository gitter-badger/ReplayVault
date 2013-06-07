package net.tomasherman.replayvault.client

import dispatch.:/._
import java.io._
import net.tomasherman.replayvault.client.Utils._
import dispatch.{Http, url, :/, Request}

/**
 * Created by IntelliJ IDEA.
 * User: arg
 * Date: 10/21/11
 * Time: 4:12 PM
 * To change this template use File | Settings | File Templates.
 */


class APIConfig(url: String, port: Int) {
  val apiRoot = :/(url,port)
  val upload = apiRoot / "api" / "upload"
  def newAccout = apiRoot / "account" / "new"
  def listBnets = apiRoot / "bnet_account" / "list"
  def newBnet = apiRoot / "bnet_account" / "new"
}

class StaticConfig {
  val isReplayPredicate: (File) => Boolean = _.getName.endsWith(".SC2Replay")
}

class FileConfig(val appRoot: File) {
  val logFile = appRoot / "uploader.log"
  val cfgFile = appRoot / "config.cfg"
  val cacheFile = appRoot / "cache.txt"
  val changelog = appRoot / "changelog.txt"
}

trait DynamicConfig {
  def scanDirs: Set[File]
  def accountName: String
}



  
