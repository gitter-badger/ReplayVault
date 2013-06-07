package net.tomasherman.replayvault.client.logic

import java.io.File
import java.net.URL

object ReplayState extends Enumeration {
  type ReplayState = Value
  val NOTINTERESTING = Value("Not interesting")
  val UPLOADED = Value("Uploaded")
  val FAILED = Value("Failed")
}

case class ReplayModel(file: File, hash: String)(val url: Option[URL] = None)
