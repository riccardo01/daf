package it.gov.daf.datasetmanager

import java.io.File
import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import it.gov.daf.storagemanager.ActionAnyContent
import dataset_manager.yaml.StorageContent
import it.gov.daf.storagemanager.client.Storage_managerClient
import play.api.libs.ws.ahc.AhcWSClient

import scala.concurrent.Future
import scala.util.{Failure, Success}


object StorageCaller {

  import scala.concurrent.ExecutionContext.Implicits.global

  val uriCatalogManager = ConfigFactory.load().getString("WebServices.storageUrl")

  def getDataset(format: String, chunk_size: Option[Int], uri: String, Authorization: String, limit: Option[Int]) // : Future[Successfull]
  = {
    println(format)
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val client: AhcWSClient = AhcWSClient()
    val storageManager = new Storage_managerClient(client)(uriCatalogManager)

    //val service = s"$uriCatalogManager/dataset-catalogs/$uri"
    //val response = ingestionManager.connect(client)(service)

    val logical_uri = URLEncoder.encode(uri, "UTF-8")
    val response = storageManager.getDataset(format: String, chunk_size: Option[Int], uri: String, Authorization: String, limit: Option[Int])
    val res: Future[StorageContent] = response
      .map{
        case ActionAnyContent(value) => StorageContent(value)
        case ex => StorageContent(Some(s"ERROR $ex"))
      }
    res
  }

}
