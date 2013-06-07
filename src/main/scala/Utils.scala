package net.tomasherman.replayvault.client

import java.io._
import java.security.MessageDigest


/**
 * Created by IntelliJ IDEA.
 * User: arg
 * Date: 10/21/11
 * Time: 4:24 PM
 * To change this template use File | Settings | File Templates.
 */

object Utils {

  def file(s: String) = new File(s)

  implicit def file2pimped(f: File) = { 
    new PimpedFile(f)
  }

  case class PimpedFile(f: File) { 
    def /(suffix: String) = { 
      new File(f,suffix)
    }

    def existsOr(onFail: File => Unit) = { 
      if(!f.exists()) {
	onFail(f)
      }
      f
    }
    
    def existsOrFail() = { 
      f.existsOr({ ff => throw new Exception("File %s not found!" format ff) })
    }
    
    def existsOrCreate()  = { 
      f.existsOr({ ff => ff.createNewFile() })
    }

    def isDirOrFail() = { 
      f.existsOrFail()
      if(!f.isDirectory()) { 
	throw new Exception("%s is not a directory!" format f)
      }
      f
    }
  }
  
}
