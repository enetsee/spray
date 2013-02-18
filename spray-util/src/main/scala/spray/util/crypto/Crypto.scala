package spray.util
package crypto

import javax.crypto._
import javax.crypto.spec.SecretKeySpec
import spray.util.codecs.Codecs
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object Crypto {

  
  val secret =  ConfigFactory.load().getString("application-secret")
  
  
  def sign(message: String, key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message.getBytes("utf-8")))
  }
  
  

  def sign(message: String): String =     
    sign(message, secret.getBytes("utf-8"))
  

}