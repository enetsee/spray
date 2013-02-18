package spray.routing
package directives

import shapeless._
import spray.util._
import spray.util.crypto._
import spray.http._
import HttpHeaders._

case class SessionCookie(
  data: Map[String, String] = Map.empty[String, String],
  expires: Option[DateTime] = None,
  maxAge: Option[Long] = None,
  domain: Option[String] = None,
  path: Option[String] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  extension: Option[String] = None) {

  def get(key: String) = data.get(key)

  def isEmpty: Boolean = data.isEmpty

  def +(kv: (String, String)) = copy(data + kv)

  def -(key: String) = copy(data - key)

  def apply(key: String) = data(key)

}

trait SessionCookieDirectives {
  import BasicDirectives._
  import RouteDirectives._
  import HeaderDirectives._
  import RespondWithDirectives._
  import CookieDirectives._

  
  def sessionCookie: Directive[SessionCookie :: HNil] = headerValue {
    case Cookie(cookies) => cookies.find(_.name ==  ConfigUtils.referenceConfig.getString("session-cookie-name")) map { fromCookie }
    case _ => None
  } | reject(MissingSessionCookieRejection)

  def optionalSession: Directive[Option[SessionCookie] :: HNil] =
    sessionCookie.hmap(_.map(shapeless.option)) | provide(None)

  def setSession(session: SessionCookie): Directive0 = {
    respondWithHeader(`Set-Cookie`(session))
  }

  def deleteSession(domain: String = "", path: String = ""): Directive0 =
    deleteCookie(ConfigUtils.referenceConfig.getString("session-cookie-name"), domain, path)

  implicit def fromCookie(cookie: HttpCookie): SessionCookie =
    SessionCookie(decode(cookie.content), cookie.expires, cookie.maxAge, cookie.domain, cookie.path, cookie.secure, cookie.httpOnly, cookie.extension)

  implicit def toCookie(session: SessionCookie): HttpCookie =
    HttpCookie(ConfigUtils.referenceConfig.getString("session-cookie-name"), encode(session.data), session.expires, session.maxAge, session.domain, session.path, session.secure, session.httpOnly, session.extension)

  private def encode(data: Map[String, String]): String = {
    val encoded = java.net.URLEncoder.encode(data.filterNot(_._1.contains(":")).map(d => d._1 + ":" + d._2).mkString("\u0000"), "UTF-8")
    Crypto.sign(encoded) + "-" + encoded
  }

  private def decode(data: String): Map[String, String] = {
    def urldecode(data: String) = java.net.URLDecoder.decode(data, "UTF-8").split("\u0000").map(_.split(":")).map(p => p(0) -> p.drop(1).mkString(":")).toMap
    // Do not change this unless you understand the security issues behind timing attacks.
    // This method intentionally runs in constant time if the two strings have the same length.
    // If it didn't, it would be vulnerable to a timing attack.
    def safeEquals(a: String, b: String) = {
      if (a.length != b.length) false
      else {
        var equal = 0
        for (i <- Array.range(0, a.length)) { equal |= a(i) ^ b(i) }
        equal == 0
      }
    }
    
    try {
      val splitted = data.split("-")
      val message = splitted.tail.mkString("-")
      if (safeEquals(splitted(0), Crypto.sign(message)))
        urldecode(message)
      else
        Map.empty[String, String]
    } catch {
      // fail gracefully is the session cookie is corrupted
      case scala.util.control.NonFatal(_) => Map.empty[String, String]
    }
  }

}