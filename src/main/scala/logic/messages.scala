package net.tomasherman.replayvault.client.logic

import java.io.File
import ReplayState._
import org.streum.configrity.Configuration

sealed trait ModelActorMsg
case class GetModel() extends ModelActorMsg
case class GetHashes() extends ModelActorMsg
case class UpdateModel(model: ReplayModel, newState: ReplayState) extends ModelActorMsg
case class SaveCache() extends ModelActorMsg
case class GetURL(hash: String) extends ModelActorMsg
case class GetTimes() extends ModelActorMsg

sealed trait ReplayScannerMsg
case class Rescan() extends ReplayScannerMsg

sealed trait ReplayUploaderMsg
case class Upload(replay: ReplayModel) extends ReplayUploaderMsg

sealed trait ConfigActorMsgs
case class GetConfig() extends ConfigActorMsgs
case class UpdateKey(key: String, value: Any) extends ConfigActorMsgs
case class UpdateListKey(key: String, value: List[Any]) extends ConfigActorMsgs
case class DeleteKey(key: String) extends ConfigActorMsgs
case class Subscribe() extends ConfigActorMsgs

case class ConfigPush(cfg: Configuration) 
