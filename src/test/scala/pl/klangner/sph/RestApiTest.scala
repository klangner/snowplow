package pl.klangner.sph

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}
import pl.klangner.sph.ApiObjects.ApiResponse
import spray.json._


class RestApiTest extends WordSpec with Matchers with ScalatestRouteTest with SprayJsonSupport {

  private def mainRoute() = {
    MainApp.route(new SchemaDB())
  }

  private val configSchema =
    """
      |{
      |  "$schema": "http://json-schema.org/draft-04/schema#",
      |  "type": "object",
      |  "properties": {
      |    "source": {
      |      "type": "string"
      |    },
      |    "destination": {
      |      "type": "string"
      |    },
      |    "timeout": {
      |      "type": "integer",
      |      "minimum": 0,
      |      "maximum": 32767
      |    },
      |    "chunks": {
      |      "type": "object",
      |      "properties": {
      |        "size": {
      |          "type": "integer"
      |        },
      |        "number": {
      |          "type": "integer"
      |        }
      |      },
      |      "required": ["size"]
      |    }
      |  },
      |  "required": ["source", "destination"]
      |}
    """.stripMargin

  private val correctDoc =
    """
      |{
      |  "source": "/home/alice/image.iso",
      |  "destination": "/mnt/storage",
      |  "timeout": 100,
      |  "chunks": {
      |    "size": 1024,
      |    "number": 12
      |  }
      |}
    """.stripMargin

  private val correctDoc2 =
    """
      |{
      |  "source": "/home/alice/image.iso",
      |  "destination": "/mnt/storage",
      |  "timeout": null,
      |  "chunks": {
      |    "size": 1024,
      |    "number": null
      |  }
      |}
    """.stripMargin

  private val wrongDoc =
    """
      |{
      |  "source": "/home/alice/image.iso",
      |  "destination": "/mnt/storage",
      |  "timeout": null,
      |  "chunks": {
      |    "number": null
      |  }
      |}
    """.stripMargin

  private val invalidDoc =
    """
      |{
      |  "source":
      |}
    """.stripMargin

  private def uploadSchemaRequest(schema: String): HttpRequest = {
    HttpRequest(
      HttpMethods.POST,
      uri = "/schema/1",
      entity = HttpEntity(schema))
  }

  private def uploadDocument(doc: String, schema: String = "1"): HttpRequest = {
    HttpRequest(
      HttpMethods.POST,
      uri = s"/validate/$schema",
      entity = HttpEntity(doc))
  }

  "The service" should {

    "have healthcheck" in {
      Get("/healthcheck") ~> mainRoute() ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "upload valid schema" in {
      uploadSchemaRequest(configSchema) ~> mainRoute() ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("uploadSchema", "1", "success", "")
      }
    }

    "return error id upload schema is not valid" in {
      uploadSchemaRequest("not json") ~> mainRoute() ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("uploadSchema", "1", "error", "Invalid JSON")
      }
    }

    "verify upload" in {
      val route = mainRoute()
      val expected = configSchema.parseJson
      uploadSchemaRequest(configSchema) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("uploadSchema", "1", "success", "")
      }
      Get("/schema/1") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val received = responseAs[JsValue]
        received shouldEqual expected
      }
    }

    "error when download wrong id" in {
      Get("/schema/not-id") ~> mainRoute() ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "validate correct document" in {
      val route = mainRoute()
      uploadSchemaRequest(configSchema) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("uploadSchema", "1", "success", "")
      }

      uploadDocument(correctDoc) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("validateDocument", "1", "success", "")
      }
    }

    "clean document" in {
      val route = mainRoute()
      uploadSchemaRequest(configSchema) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("uploadSchema", "1", "success", "")
      }

      uploadDocument(correctDoc2) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("validateDocument", "1", "success", "")
      }
    }

    "not validate wrong schema" in {
      val route = mainRoute()
      uploadSchemaRequest(configSchema) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("uploadSchema", "1", "success", "")
      }

      uploadDocument(correctDoc, "no-id") ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("validateDocument", "no-id", "error", "Schema not found")
      }
    }

    "not validate wrong document" in {
      val route = mainRoute()
      uploadSchemaRequest(configSchema) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("uploadSchema", "1", "success", "")
      }

      uploadDocument(wrongDoc) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val received = responseAs[ApiResponse]
        received.action shouldEqual "validateDocument"
        received.id shouldEqual "1"
        received.status shouldEqual "error"
      }
    }

    "not validate wrong json" in {
      val route = mainRoute()
      uploadSchemaRequest(configSchema) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ApiResponse] shouldEqual ApiResponse("uploadSchema", "1", "success", "")
      }

      uploadDocument(invalidDoc) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        val received = responseAs[ApiResponse]
        received.action shouldEqual "validateDocument"
        received.id shouldEqual "1"
        received.status shouldEqual "error"
      }
    }
  }
}