package net.tomasherman.replayvault.client.logic
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.net.URL
import java.util.Date
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scalax.file.Path
import scalax.io.Input
import scalax.io.Output
import scalax.io.Resource
import scala.util.Random._

class FSModelIOSpecs extends Specification {

  private trait DefaultScope extends Scope with Mockito with ThrownExpectations {
    val p = mock[Path]
    val io = new FSModelIO(p)
    val hash = "SOMEHASH"
    val validURLText = "http://ggtracker.com/someurl"
    val validURL = new URL(validURLText)
    val validD = new Date
    val validDate = validD.getTime.toString

    def makeDate(str:String) = new Date(str.toLong)
  }

  "parseLine" should {
    "parse url and date properly" in new DefaultScope {
      io.parseLine(hash + "|" + validDate) must_== Some((hash,makeDate(validDate),None))
    }
    
    "parse hash,date and url properly" in new DefaultScope {
      io.parseLine(hash + "|" + validDate  + "|" + validURLText) must_== Some((hash,makeDate(validDate),Some(validURL)))
    }

    "return none for too many | separators" in new DefaultScope {
      io.parseLine(hash + "|" + validDate + "|" + validURLText + "|" + "fail") must_== None
    }
  
    "return none  when url is invalid" in new DefaultScope {
      val urlText ="this ain't valid url"
      io.parseLine(hash + "|" + validDate + "|" + urlText) must_== None
    }

    "return none when date is invalid" in new DefaultScope {
      io.parseLine(hash + "|this aint no date|" + validURLText) must_== None
    }
  }

  "formatLine" should {
    "format line properly for hash" in new DefaultScope {
      io.formatLine((hash,validD,None)) must_== ("%s|%s" format (hash,validDate))
    }
    "format line properly for hash and url" in new DefaultScope {
      io.formatLine((hash,validD,Some(validURL))) must_== ("%s|%s|%s" format (hash,validDate,validURLText))
    }
  }
}


class ModelActorFunctionalitySpecs extends Specification {
  import ReplayState._
  private trait DefaultScope extends Scope with Mockito with ThrownExpectations {
    val io = mock[ModelIO]
    class m(val io: ModelIO) extends ModelActorFunctionality
    val out = mock[Output]
    val model = new m(io)


    def randomTriplets(len: Int = 5) = {
      randomPairs(len) map { x => (x._1,new Date, x._2)}
    }
    
    def randomPairs(len: Int = 5) = {
      (0 to len) map { _ => nextBoolean match {
        case true => (randomString,None)
        case false => (randomString,randomURL)
      }
    }}

    def urlsFromData(data: Seq[(String,Date,Option[URL])]) = {
      data.filter(_._3.isInstanceOf[Some[_]]).map(x => (x._1,x._3.get)).toMap
    }

    def datesFromData(data: Seq[(String,Date,Option[URL])]) = {
      data map { x => (x._1,x._2) } toMap
    }
  
    def randomURL = Some(new URL("http://" + randomString ))
    
    def randomString = alphanumeric.take(16).mkString
  }

  "updateUploadedTime" should {
    "work fine when no record for hash is known" in new DefaultScope {
      val hash = "somehash"
      model.uploadedTime.contains(hash) must_== false
      model.updateUploadTime(hash)
      model.uploadedTime.contains(hash) must_== true
    }
    "must not update date for known hash" in new DefaultScope {
      val hash = "somehash"
      val date = mock[Date]
      model.uploadedTime = Map(hash -> date)
      model.updateUploadTime(hash)
      model.uploadedTime(hash) must_== date
    }
  }

  "updateModel" should {
    "add model properly" in new DefaultScope {
      val repl = mock[ReplayModel]
      repl.hash returns "hash"
      val state = mock[ReplayState]
      
      model.uploadedTime.contains(repl.hash) must_== false
      model.updateModel(repl,state)
      model.model must_== Map(repl -> state)
      model.uploadedTime.contains(repl.hash) must_== false //only add uploadedTime for uploaded state
    }
    "update model properly" in new DefaultScope {
      val repl = mock[ReplayModel]
      repl.hash returns "hash"
      val firstState = mock[ReplayState]
      val postUpdateState = mock[ReplayState]
      val d = mock[Date]
      model.uploadedTime = Map(repl.hash -> d)
      model.model = Map(repl -> firstState)
      model.updateModel(repl,postUpdateState)
      model.model must_== Map(repl -> postUpdateState)
      model.uploadedTime(repl.hash) must_== d
    } 
    "add hash to cache if state == UPLOADED" in new DefaultScope {
      val repl = mock[ReplayModel]
      val url = new URL("http://someurl.com")
      repl.hash returns "HASH"
      repl.url returns Some(url)
      model.uploadedTime.contains(repl.hash) must_== false

      model.updateModel(repl,UPLOADED)

      model.cache must_== Set(repl.hash)
      model.model must_== Map(repl -> UPLOADED)
      model.urls must_== Map(repl.hash -> url)
      model.uploadedTime.contains(repl.hash) must_== true
    }
  }

  "updateCache" should {
    "add hash into cache" in new DefaultScope {
      val expected = "SOME STIRNG"
      val prevCache = Set("hash","otherhash")
      model.cache = prevCache
      model.updateCache(expected)
      model.cache.contains(expected) must_== true
    }
  }

  "loadCache" should {
    "load data properly" in new DefaultScope {
      val data = randomTriplets()
      val hashes = data map (_._1)
      io.loadCache returns data.toSet
      model.loadCache()
      model.cache must_== hashes.toSet
      model.uploadedTime must_== datesFromData(data)
      model.urls must_== urlsFromData(data)
    }
  }

  "saveCache" should {
    "save data with empty model properly" in new DefaultScope {
      val data = randomTriplets()
      val hashes = data map (_._1)
      model.cache = hashes.toSet
      model.uploadedTime = datesFromData(data)
      model.urls = urlsFromData(data)
      model.saveCache()
      there was one(io).saveCache(data.toSet)
    }

    "save data with some content in model properly into Output" in new DefaultScope {
      val cacheData = randomTriplets(7)
      val modelStrings = cacheData.take(3)
      val models = modelStrings map ( x => ReplayModel(mock[File],x._1)())
      val specialModel = ReplayModel(mock[File],cacheData.head._1)() //this model has same hash as one of the hashes in cache, in the output file, the cache should appear only once
      val modelMap = Map(models.head -> FAILED, specialModel -> UPLOADED) ++ models.tail.map (_ -> UPLOADED).toMap //first modelfrom models failed to upload, it's has shouldn't appear in the output file

      val expected = cacheData ++ modelStrings.tail
      
      model.cache = (cacheData map (_._1)).toSet
      model.model = modelMap
      model.uploadedTime = datesFromData(cacheData)
      model.urls = urlsFromData(cacheData)
      model.saveCache()

      there was one(io).saveCache(expected.toSet)
    }
  }

}
 
