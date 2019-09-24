package pl.klangner.sph

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, MediaTypes, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchemaFactory
import org.slf4j.LoggerFactory
import pl.klangner.sph.ApiObjects.ApiResponse
import spray.json.{DefaultJsonProtocol, _}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try


object MainApp extends SprayJsonSupport with DefaultJsonProtocol {

  private val Log = LoggerFactory.getLogger(MainApp.getClass.getName)

  // Akka HTTP
  implicit val system: ActorSystem = ActorSystem("snowplow-app")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // Json schema
  lazy val jsonSchemaFactory: JsonSchemaFactory = JsonSchemaFactory.byDefault

  /** Main route of the application */
  def route(db: SchemaDB): Route = {

    path("schema" / Segment ) { id =>
      post {
        entity(as[String]) { json =>
          postSchema(db, id, json)
        }
      }
    } ~ path("schema" / Segment ) { id =>
      get {
        getSchema(db, id)
      }
    } ~ path("validate" / Segment) { id =>
      post {
        entity(as[String]) { json =>
          validateDocument(db, id, json)
        }
      }
    } ~ path("healthcheck") {
      get {
        complete(StatusCodes.OK)
      }
    }
  }

  def postSchema(db: SchemaDB, id: String, schema: String): StandardRoute = {
    val jsonTry = Try(schema.parseJson)

    if (jsonTry.isSuccess) {
      db.put(id, schema)
      val response = ApiResponse("uploadSchema", id, "success", "")
      complete(HttpResponse(StatusCodes.Created, entity = asHttpEntity(response)))
    } else {
      val response = ApiResponse("uploadSchema", id, "error", "Invalid JSON")
      complete(HttpResponse(StatusCodes.BadRequest, entity = asHttpEntity(response)))
    }

  }

  def getSchema(db: SchemaDB, id: String): StandardRoute  = {
    db.fetch(id).map { schema =>
      complete(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(MediaTypes.`application/json`, schema)
      ))
    }.getOrElse(complete(StatusCodes.NotFound))
  }

  def validateDocument(db: SchemaDB, schemaId: String, doc: String): StandardRoute = {
    db.fetch(schemaId).map { schema =>
      validateWithSchema(schema, doc) match {
        case Left(msg) =>
          val response = ApiResponse("validateDocument", schemaId, "error", msg)
          complete(HttpResponse(StatusCodes.OK, entity = asHttpEntity(response)))
        case Right(_) =>
          val response = ApiResponse("validateDocument", schemaId, "success","")
          complete(HttpResponse(StatusCodes.OK, entity = asHttpEntity(response)))
      }

    }.getOrElse {
      val response = ApiResponse("validateDocument", schemaId, "error", "Schema not found")
      complete(HttpResponse(StatusCodes.NotFound, entity = asHttpEntity(response)))
    }
  }

  def asHttpEntity(response: ApiResponse) = HttpEntity(MediaTypes.`application/json`, response.toJson.compactPrint)


  /**
    * Validate given document against schema.
    * Return Unit if document is valid or error message in case of error
    */
  def validateWithSchema(schemaString: String, json: String): Either[String, Unit] = {
    try {
      val schemaNode: JsonNode = JsonLoader.fromString(schemaString)
      val schema = jsonSchemaFactory.getJsonSchema(schemaNode)
      val doc: JsonNode = JsonLoader.fromString(json)
      val cleanedDoc: JsonNode = cleanDocument(doc)
      val validationResult: ProcessingReport = schema.validate(cleanedDoc)
      if (validationResult.isSuccess) {
        Right(())
      } else {
        val it = validationResult.iterator()
        if (it.hasNext) {
          Left(it.next().getMessage)
        } else {
          Left("Unknown error")
        }
      }
    } catch {
      case e: JsonParseException => Left(e.getMessage)
    }
  }

  /**
    * Remove null nodes from the document
    * This function will mutate its input argument!
    * If this is a problem then we can start with deepcopy first.
    * Not sure what is preferred here.
    */
  def cleanDocument(node: JsonNode): JsonNode = {
    val it = node.iterator
    while (it.hasNext) {
      val child = it.next
      if (child.isNull) it.remove()
      else cleanDocument(child)
    }
    node
  }

  /** App main entry */
  def main(args: Array[String]) {
    Log.info("Server started")
    val schemaDB = new SchemaDB("./schema.db")
    Await.result(Http().bindAndHandle(route(schemaDB), "0.0.0.0", 8080), Duration.Inf)
  }
}