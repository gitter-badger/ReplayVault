package net.tomasherman.replayvault.client.logic

import akka.actor.{Actor, ActorRef}
import akka.pattern._
import akka.dispatch.ExecutionContext
import akka.util.Timeout
import com.codahale.logula.Logging
import dispatch._
import java.io._
import net.tomasherman.replayvault.client._
import org.scala_tools.subcut.inject._
import scala.xml.NodeSeq
import scala.xml.XML
import org.streum.configrity._
import org.streum.configrity.converter.Extra._
import java.net.URL
import akka.util.duration._
trait Status
case class OkStatus(url: String, message: Option[String] = None) extends Status
case class FailureStatus(code: Int, message: String, url: Option[String] = None ) extends Status
case class InvalidResponseStatus(response: String) extends Status

class HttpActor(val cache: ActorRef, val config: ActorRef)(implicit val bindingModule: BindingModule)
   extends Actor with HttpFunctionality with Injectable {

  implicit val execCtx = inject[ExecutionContext]
  val apiCfg = inject[APIConfig]
  var apiUpload = :/("stub")

  var builder = new SC2GearsBuilder(apiUpload, new InputResourceCreator)
  val httpExecutor = new DispatchExecutor
  val resolver = new SC2GearsXMLResolver
  
  val handleUploadFailure = (status: FailureStatus, model: ReplayModel) => { cache ! UpdateModel(model, ReplayState.FAILED) }
  val handleUploadSuccess = (status: OkStatus, model: ReplayModel) => { cache ! UpdateModel(new ReplayModel(model.file,model.hash)(Some(new URL(status.url))), ReplayState.UPLOADED) }
  val handleInvalidStatus = (status: InvalidResponseStatus, model: ReplayModel)=> {}
  override def preStart {
    config ! Subscribe()
  }

  def updateConfig(cfg: Configuration) {
    apiUpload = cfg.get[URL](ConfigKeys.service).map({x: URL => url(x.toString)}).getOrElse(apiUpload)
    
    for ( name <- cfg.get[String](ConfigKeys.username);
          pass <- cfg.get[String](ConfigKeys.password)
    ){
      log.info("Using new credentials: %s, %s", name, pass)
      builder = new SC2GearsBuilder(apiUpload,new InputResourceCreator,Some((name,pass)))
    }
    builder = new SC2GearsBuilder(apiUpload,new InputResourceCreator,None)
  }
  
  def receive = {
    case m @ Upload(model) => uploadFile(model)
    case ConfigPush(cfg) => {
      updateConfig(cfg)
    }
  }
}


trait HttpFunctionality extends Logging{ 
  val httpExecutor: HttpExecutor
  def builder: RequestBuilder
  val resolver: UploadResolver
  def apiUpload: Request
  def handleUploadSuccess:(OkStatus, ReplayModel) => Unit
  def handleUploadFailure:(FailureStatus, ReplayModel) => Unit
  def handleInvalidStatus:(InvalidResponseStatus, ReplayModel) => Unit

  def uploadFile(model: ReplayModel) = { 
    try {
      val invoke = builder.buildRequest(model.hash,model.file.getName(),model.file)
      val res = httpExecutor.executeAsString(invoke)
      handleResult(resolver.processResult(res,model),model)
    } catch {
      case x => {
        log.warn(x, "error: model: %s", model)
        handleUploadFailure(new FailureStatus(99,x.getMessage()),model)
      }
    }
  }

  def handleResult(status: Status,model:ReplayModel) {
    status match {
      case x: OkStatus => handleUploadSuccess(x, model)
      case x: FailureStatus => handleUploadFailure(x,model)
      case x: InvalidResponseStatus => handleInvalidStatus(x,model)
    }
  }
}


trait UploadResolver {
  def processResult(result: String, model: ReplayModel): Status
}

class SC2GearsXMLResolver extends UploadResolver {
  def textOpt(nodeSeq: NodeSeq, key: String) = (nodeSeq \\ key).headOption map (_.text.trim)
  def processResult(result: String, model: ReplayModel) = {
    val xml = XML.loadString(result)
    (xml \\ "uploadResult").headOption flatMap { res =>
      textOpt(res,"errorCode") flatMap { code =>
        code.toInt match {
          case 0 => textOpt(res,"replayUrl") map {
            OkStatus(_,textOpt(res,"message"))
          }
          case x => textOpt(res,"message") map {
            FailureStatus(x,_,textOpt(res,"replayUrl"))
          }
        }        
      }
    } getOrElse(InvalidResponseStatus(result))
  }
}
  
trait HttpExecutor {
  def executeAsString(req: Request): String
}

class DispatchExecutor extends HttpExecutor {
  object SilentHttp extends Http { 
    override lazy val log = new Logger { 
      def info(msg: String, items: Any*) { }
      def warn(msg: String, items: Any*) { }
    }
  }
  def executeAsString(req: Request) = SilentHttp(req as_str)
}

trait Base64Support {
  import org.apache.commons.codec.binary.Base64
  private val codec = new Base64(10,Array(),false)

  def encode(data: Array[Byte]) = new String(codec.encode(data))
}

trait RequestBuilder {
  def buildRequest(hash: String, fileName: String, data: File): Request
}

class SC2GearsBuilder(
  val handler: Request,
  val resCreator: InputCreator,
  val credentials: Option[(String,String)] = None
)
extends RequestBuilder with Base64Support {
  
  def buildRequest(hash: String, fileName: String, data: File) = {
    handler << buildMap(hash,fileName,data)
  }
  def buildMap(hash: String, fileName: String, data: File) = {
    val bin = resCreator.resource(data).byteArray
    Map(
      "requestVersion" -> "1.0",
      "userName" -> credentials.map(_._1).getOrElse(""),
      "password" -> credentials.map(_._2).getOrElse(""),
      "fileMd5"  -> hash.toLowerCase,
      "fileSize" -> bin.length.toString,
      "description" -> "",
      "fileName" -> fileName,
      "fileContent" -> encode(bin)
    )
  }
}
