package it.gov.daf.microsrv.utilities

import java.io.File
import java.net.URLEncoder

import dataset_manager.yaml.Credentials
import it.gov.daf.common.authentication.Authentication
import org.apache.commons.net.util.Base64
import play.api.libs.json
import play.api.libs.json.{JsArray, JsError, JsObject, JsString, JsValue}
import play.api.mvc.Request

import scala.util.parsing.json.JSONObject

//import akka.actor.ActorSystem
//import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.asynchttpclient.AsyncHttpClientConfig
import play.api.libs.ws.WSConfigParser
import play.api.libs.ws.ahc.{AhcConfigBuilder, AhcWSClientConfig}
import play.api.{Configuration, Environment, Mode}
import base64.Decode.{ urlSafe => fromBase64UrlSafe }

/**
  * Created by ale on 11/05/17.
  */

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Throw",
    "org.wartremover.warts.ToString",
    "org.wartremover.warts.Null"
  )
)
object WebServiceUtil {

  val configuration = Configuration.reference ++ Configuration(ConfigFactory.parseString(
    """
      |ws.followRedirects = true
    """.stripMargin))

  // If running in Play, environment should be injected

  val environment = Environment(new File("."), this.getClass.getClassLoader, Mode.Prod)

  Authentication(Configuration.load(Environment.simple()), null)

  val parser = new WSConfigParser(configuration, environment)
  val config = new AhcWSClientConfig(wsClientConfig = parser.parse())
  val builder = new AhcConfigBuilder(config)
  val logging = new AsyncHttpClientConfig.AdditionalChannelInitializer() {
    override def initChannel(channel: io.netty.channel.Channel): Unit = {
      val _ = channel.pipeline.addFirst("log", new io.netty.handler.logging.LoggingHandler("debug"))
    }
  }

  val ahcConfig = builder.configure()
    .setHttpAdditionalChannelInitializer(logging)
    .build

  def buildEncodedQueryString(params: Map[String, Any]): String = {
    val encoded = for {
      (name, value) <- params if value != None
      encodedValue = value match {
        case Some(x)         => URLEncoder.encode(x.toString, "UTF8")
        case x               => URLEncoder.encode(x.toString, "UTF8")
      }
    } yield name + "=" + encodedValue

    encoded.mkString("?", "&", "")
  }


  def readCredentialFromRequest( request:Request[Any] ) :Credentials ={

    val auth = request.headers.get("authorization")

    val (authType, user) = auth.fold(("", "")) { au =>
      val creds = au.split(" ")
      (creds.head, creds.tail.headOption.getOrElse(""))
    }
    // val authType: String = auth.fold("")( au => au.split(" "))

    authType.toLowerCase match {
      case "basic" =>
        val userAndPass = new String(Base64.decodeBase64(user.getBytes)).split(":")
        Credentials( Option(userAndPass(0)), Option(userAndPass(1)) )
      case "bearer" =>
        val claims = Authentication.getClaims(request)
        val user:Option[String] = for {
          c <- claims
          s <- c.get("sub")
        } yield s.toString

        println(s"JWT user: $user")
        Credentials(user , None)
      case _ =>       throw new Exception("Authorization header not found")
    }

    // if ( authType.equalsIgnoreCase("basic") ) {

    //   val userAndPass = new String(Base64.decodeBase64(auth.get.split(" ").drop(1).head.getBytes)).split(":")
    //   Credentials( Option(userAndPass(0)), Option(userAndPass(1)) )

    // } else if( authType.equalsIgnoreCase("bearer") ) {

    //   val user:Option[String] = Option( Authentication.getClaims(request).get.get("sub").get.toString )
    //   println("JWT user:"+user)
    //   Credentials(user , None)

    // } else
    //   throw new Exception("Authorization header not found")


    //val userAndPass = if (auth.get.contains(" ")) new String(Base64.decodeBase64(auth.get.split(" ").drop(1).head.getBytes)).split(":")
    //                  else new String(Base64.decodeBase64(auth.get.getBytes)).split(":")


  }

  def cleanDquote(in:String): String = {
    in.replace("\"","").replace("[","")replace("]","")
  }

  def getMessageFromJsError(error:JsError): String ={

    val jsonError = JsError.toJson(error)

    if( (jsonError \ "obj").toOption.isEmpty )
      jsonError.value.foldLeft("ERRORS--> "){ (s: String, pair: (String, JsValue)) =>
        s + "field: "+pair._1 +" message:"+ (pair._2 \\ "msg")(0).toString + "  "
      }
    else
      cleanDquote( (( (jsonError \ "obj")(0) \ "msg").getOrElse(JsArray(Seq(JsString(" ?? "))))(0) ).get.toString() )

    //if( error.errors.length > 1 )
    //cleanDquote( (((JsError.toJson(error) \ "obj[0].theme").getOrElse(JsArray(Seq(JsString("  "))))(0) \ "msg").getOrElse(JsArray(Seq(JsString("  "))))(0) ).get.toString() )
    //else

    //cleanDquote( (((JsError.toJson(error) \ "obj").getOrElse(JsArray(Seq(JsString("  "))))(0) \ "msg").getOrElse(JsArray(Seq(JsString("  "))))(0) ).get.toString() )
  }

  def getMessageFromCkanError(error:JsValue): String ={


    val errorMsg = (error \ "error").getOrElse(JsString("can't retrive error") )
    //val message = (errorMsg \ "message").getOrElse( ((errorMsg \ "name")(0)).getOrElse(JsString(" can't retrive error ")) )

    val ckanError = errorMsg.as[JsObject].value.foldLeft("ERRORS: "){ (s: String, pair: (String, JsValue)) =>
      s + "<< field: "+pair._1 +"  message: "+ cleanDquote(pair._2.toString()) + " >>   "}

    /*
    val ckanError = cleanDquote( (errorLookup \ "message").getOrElse(JsString(" can't retrive error ")).toString() ) + " (" +
                    cleanDquote( (errorLookup \ "__type").getOrElse(JsString(" can't retrive error type ")).toString() )+ ")"
    */
    //println("---->"+ckanError)

    ckanError

  }

}
