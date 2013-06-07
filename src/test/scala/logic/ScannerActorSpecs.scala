package net.tomasherman.replayvault.client.logic

import com.codahale.logula.Logging
import java.io.File
import org.mockito.Matchers
import org.mockito.Mockito._
import org.specs2.matcher.ThrownExpectations
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock._
import org.specs2.mutable._
import org.specs2.specification.Scope
import org.mockito.Matchers._
import scalax.io.Resource

class ReplayFinderSpecs extends Specification {
  private trait DefaultScope extends Scope with Mockito with ThrownExpectations{
    val c = new ReplayFinder {}
    val file = mock[File]
    val cache = mock[Set[String]]
  }
  "findReplays" should {
    "throw exception for non-directory file" in new DefaultScope {
      file.isDirectory() returns false
      c.findReplays(file,{ _ => true }) must throwA[Exception]
    }

    "return proper set of files" in new DefaultScope {
      file.isDirectory() returns true
      val files = Array.fill(5)(mock[File])
      val replays = Seq(files(1),files(2),files(4))
      file.listFiles returns files
      val isReplay = {f:File => replays.contains(f)}
      c.findReplays(file,isReplay) must_== replays
    }
  }
}
  
class MD5FileInCacheCheckerSpecs extends Specification {

  protected trait DefScope extends Scope with Mockito with ThrownExpectations {
    val string = "SOME RANDOM STRING!"
    val expected = "e3408d504d034da9a25a57a6cada477b".toUpperCase

    def stringRes(str: String) =
      Resource.fromInputStream(new java.io.ByteArrayInputStream(str.getBytes))
  }

  "calculateHash" should {
    "calculate proper hash" in new DefScope {
      val checker = new MD5FileInCacheChecker()
      val h = checker.calculateHash(stringRes(string))
      h must_== expected
    }
  }

  "uncachedFile" should {
    "return true if not in cache" in new DefScope {
      val cache = Set("hi", "there")
      val file = mock[File]
      val creator = mock[InputCreator]
      creator.resource(file).returns(stringRes(string))
      val checker = new MD5FileInCacheChecker(creator)
      checker.uncachedFile(cache,file) must_== (true, (expected,file))
    }

    "return false if in cache" in new DefScope {
      val cache = Set("hi", "there", expected)
      val file = mock[File]
      val creator = mock[InputCreator]
      creator.resource(file).returns(stringRes(string))
      val checker = new MD5FileInCacheChecker(creator)
      checker.uncachedFile(cache, file) must_== (false, (expected,file))
    }
  }
}

class DirectoryScannerSpecs extends Specification {
  
  private trait DefaultScope extends Scope with Mockito with ThrownExpectations {
    val checker = mock[ReplayInCacheChecker]
    val isReplay= { _:File => true }
    val dirScanner = new DirectoryScanner(checker,isReplay)
  }
  
  "checkDirectory" should {
    "invoke callback for proper files" in new DefaultScope {
      val file = mock[File]
      val found = Vector.fill(3)(mock[File])
      val isFile = mock[Function1[File,Boolean]]
      val cache = mock[Set[String]]
      val uncachedCallback = mock[Function2[File,String,Unit]]
      val cachedCallback = mock[Function2[File,String,Unit]]
      checker.findReplays(any[File],any[Function1[File,Boolean]]).returns(found.toSeq)
      checker.uncachedFile(cache,found(0)) returns ((false,("someHash",found(0))))
      checker.uncachedFile(cache,found(1)) returns ((true,("hash",found(1))))
      checker.uncachedFile(cache,found(2)) returns ((true,("otherHash",found(2))))
      dirScanner.checkDirectory(cache, file, uncachedCallback, cachedCallback)
      there was one(cachedCallback).apply(found(0),"someHash")
      there was one(uncachedCallback).apply(found(1),"hash")
      there was one(uncachedCallback).apply(found(2),"otherHash")
    }
  }
}

class ScannerFunctionalitySpecs extends Specification {

  protected trait DefaultScope extends Scope with Mockito with ThrownExpectations {
    val cb = mock[Function2[File,String,Unit]]
    object func extends ScannerFunctionality {
      def isReplay(f: File) = true
      val uncachedCallback = (file:File, hash: String) => {}
      val cachedCallback = (file:File, hash: String) => {}
      val dirScanner = mock[DirectoryScanner]
      var checker = mock[ReplayInCacheChecker]
    }
    val cache = mock[Set[String]]
  }

  "scan" should {
    "invoke checkDirectory for all dirs" in new DefaultScope {
      val dirs = List.fill(5)(mock[File]).toSet
      func.scan(cache:Set[String], dirs)
      dirs foreach {
        there was one(func.dirScanner).checkDirectory(cache,_, func.uncachedCallback, func.cachedCallback)
      }
    }
  }
}
