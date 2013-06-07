package net.tomasherman.replayvault.client.logic

import net.tomasherman.replayvault.client.FileConfig
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.streum.configrity._
import org.streum.configrity.converter.Extra._
import scalax.file.Path
import scalax.io.Output
import scalax.io.Resource
import scalax.io.StandardOpenOption._
import scalax.io.managed.OutputStreamResource

class ConfigActorSpecs extends Specification {


  private trait funcScope extends Scope with Mockito {
    val cfg = Configuration()
    class Functionality(val outputFile: Path, var config: Configuration) extends ConfigActorFunctionality
    val t1 = "someKey" -> List("some","thatlol")
    val t2 = "otherKey" -> 1337
    val t3 = "lastKey"  -> new java.io.File("somefile")
    val fullCfg = cfg set(t1._1,t1._2) set(t2._1,t2._2) set(t3._1,t3._2)
    def checkIfValidFullCfg(cfg: Configuration) {
      cfg.get[List[String]](t1._1).get must_== t1._2
      cfg.get[Int](t2._1).get must_== t2._2
      cfg.get[java.io.File](t3._1).get must_== t3._2
    }
  }
  
  "ConfigActorFunctionality" should {
    "load configuration properly" in new funcScope {
      val fc = new Functionality(mock[Path],cfg)
      val src = scala.io.Source.fromBytes(fullCfg.format().getBytes())
      val loaded = fc.loadConfiguration(src)
    }

    "update config properly" in new funcScope {
      val fc = new Functionality(mock[Path],cfg set(t1._1,t1._2))
      val newC = cfg set(t2._1,t2._2) set(t3._1,t3._2)
      fc.updateConfig(newC)
      fc.config must_== newC
    }

    "persist configuration properly" in new funcScope {
      val stream = new java.io.ByteArrayOutputStream(1024)
      val res = Resource.fromOutputStream(stream)
      val fc = new ConfigActorFunctionality {
        val outputFile = mock[Path]
        val s = res
        outputFile.outputStream(WriteTruncate:_*) returns res
        var config = fullCfg
      }
      fc.persistConfiguration()
      checkIfValidFullCfg(Configuration.parse(new String(stream.toByteArray)))
    }
  }
/*
  "ConfigActor" should {
    "return proper config"
    "update config properly"
  }


}
*/
}
