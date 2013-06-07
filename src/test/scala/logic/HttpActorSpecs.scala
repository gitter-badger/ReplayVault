package net.tomasherman.replayvault.client.logic

import dispatch.Request
import java.io.File
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.specification.Scope
import scala.util.Random._
import scala.xml.NodeSeq
import scalax.io.Input

class Base64SupportSpecs extends Specification { 

  private trait DefaultScope extends Scope with ThrownExpectations{
    val encoder = new Base64Support {}   
    def test(raw: String, encoded: String) {
      encoder.encode(raw.getBytes) must_== encoded
    }
  }

  "encode" should {
    "encode stuff properly" in new DefaultScope {
      test("hi there","aGkgdGhlcmU=")
      test("man with three bottocks","bWFuIHdpdGggdGhyZWUgYm90dG9ja3M=")
      test("guild wars 2 beta starts in 2 hours, yey","Z3VpbGQgd2FycyAyIGJldGEgc3RhcnRzIGluIDIgaG91cnMsIHlleQ==")
    }
  }
}

class SC2GearsBuilderSpecs extends Specification {

  private trait DefaultScope extends Scope with Mockito with ThrownExpectations  {
    val creator = mock[InputCreator]
    val input = mock[Input]
    val req = mock[Request]
    val encoder = new Base64Support {}
    creator.resource(any[File]) returns input

    val hash = "SOMEHASH"
    val fileName = "SOMEFILE"
    val file = mock[File]
    val data = "SOMEDATA".getBytes
    input.byteArray returns data
    val expectedPartial =  Map(
      "requestVersion" -> "1.0",
      "fileMd5"  -> hash.toLowerCase,
      "fileSize" -> data.length.toString,
      "fileName" -> fileName,
      "fileContent" -> encoder.encode(data),
      "description" -> ""
    )
  }

  "buildMap" should {
    "build proper map without credentials" in new DefaultScope {
      val builder = new SC2GearsBuilder(req,creator)
      val map = builder.buildMap(hash,fileName,file)
      map must_== expectedPartial ++ Map("password" -> "","userName" -> "")
    }
    "build proepr map with credentials" in new DefaultScope {
      val creds = ("user","password")
      val builder = new SC2GearsBuilder(req,creator,Some(creds))
      val map = builder.buildMap(hash, fileName, file)
      map must_== expectedPartial ++ Map("password" -> creds._2,"userName" -> creds._1)      
    }
  }
}

class SC2GearsXmlResolverSpecs extends Specification {
  private trait scope extends Scope with Mockito with ThrownExpectations {
    val res = new SC2GearsXMLResolver
    val f = mock[ReplayModel]

    def code(c: Int) = <errorCode> { c } </errorCode>
    def msg(m: String) = <message> { m } </message>
    def url(u: String) = <replayUrl> { u } </replayUrl>
    def wrap(n: NodeSeq) = {
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + ((<uploadResult> { n } </uploadResult>) toString)
    }
  }

  "processResults" should {
    "return valid status for valid data" in new scope {
      val ok = 0
      val fail = 1
      val okCode = code(ok)
      val failCode = code(fail)
      val uurl = "SomeURL"
      val mmsg = "SomeMsg"
      val u = url(uurl)
      val m = msg(mmsg)
      res.processResult(wrap(okCode ++ u),f) must_== OkStatus(uurl,None)
      res.processResult(wrap(okCode ++ u ++ m),f) must_== OkStatus(uurl,Some(mmsg))
      res.processResult(wrap(failCode ++ m),f) must_== FailureStatus(fail,mmsg)
      res.processResult(wrap(failCode ++ m ++ u),f) must_== FailureStatus(fail,mmsg,Some(uurl))

      res.processResult(wrap(okCode),f) must beAnInstanceOf[InvalidResponseStatus] //ok code needs replayUrl
      res.processResult(wrap(okCode ++ m),f) must beAnInstanceOf[InvalidResponseStatus] //ok code needs replayUrl

      res.processResult(wrap(failCode),f) must  beAnInstanceOf[InvalidResponseStatus] //ok code needs replayUrl
      res.processResult(wrap(failCode ++ u),f) must beAnInstanceOf[InvalidResponseStatus] //ok code needs replayUrl
    }
  }
}
  
class HttpFunctionalitySpec extends Specification {
  private trait DefaultScope extends Scope with Mockito with ThrownExpectations {
    val exec = mock[HttpExecutor]
    val b = mock[RequestBuilder]
    val api = mock[Request]
    val res = mock[UploadResolver]

    val ok = mock[Function2[OkStatus,ReplayModel,Unit]]
    val fail = mock[Function2[FailureStatus,ReplayModel,Unit]]
    val inv = mock[Function2[InvalidResponseStatus,ReplayModel,Unit]]

    class functionality extends HttpFunctionality {
      val apiCfg = api
      val builder = b
      val resolver = res
      val apiUpload = api
      val httpExecutor = exec

      def handleUploadSuccess = ok
      def handleUploadFailure = fail
      def handleInvalidStatus = inv
    }

    val func = new functionality
    
    val model = mock[ReplayModel]
    val okS = mock[OkStatus]
    val failS = mock[FailureStatus]
    val invS = mock[InvalidResponseStatus]
  }

  "handleResult" should {
    "invoke handleUploadSuccess" in new DefaultScope {
      func.handleResult(okS,model)
      there was one(ok).apply(okS,model)
    }
    "invoke handleUploadFailure" in new DefaultScope {
      func.handleResult(failS,model)
      there was one(fail).apply(failS,model)
    }
    "invoke handleInvalidStatus" in new DefaultScope {
      func.handleResult(invS,model)
      there was one(inv).apply(invS,model)
    } 
  }

  "uploadFile" should {
    "invoke proper sequence of operations" in new DefaultScope{
      val file = mock[File]
      val invk = mock[Request]
      val name = "someName"
      val hash = "someHash"
      val resp = "someResponse"
      val status = mock[Status]
      model.file returns file
      file.getName() returns name
      model.hash returns hash
      b.buildRequest(anyString,anyString,any[File]) returns invk
      exec.executeAsString(any[Request]) returns resp
      res.processResult(resp,model) returns status
      func.uploadFile(model)
      there was one(b).buildRequest(hash,name,file)
      there was one(exec).executeAsString(invk)
      there was one(res).processResult(resp,model)
    }
  }
}
