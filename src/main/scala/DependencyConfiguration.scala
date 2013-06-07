package net.tomasherman.replayvault.client
import akka.actor.ActorSystem
import akka.dispatch.ExecutionContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import logic._
import org.scala_tools.subcut.inject._
import java.io.File

case class DependencyModule(val fileConfig: FileConfig, val dconf: DynamicConfig, val system: ActorSystem ) extends NewBindingModule({
mod => 
  import mod._
  bind [FileConfig] toSingle { fileConfig }
  bind [StaticConfig] toSingle { new StaticConfig }
  bind [APIConfig] toSingle { new APIConfig("ggtracker.com", 80) }
  bind [DynamicConfig] toSingle { dconf }
  bind [ActorSystem] toSingle { system }
  bind [ExecutionContext] toSingle { ExecutionContext.fromExecutorService(Executors.newCachedThreadPool()) }
})
