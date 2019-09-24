package pl.klangner.sph

import spray.json.{DefaultJsonProtocol, JsObject, JsString, JsValue, RootJsonFormat}


/**
  * If we don't care about adding 'message' field to each response,
  * then we could simplify this with `jsonFormat4(ApiResponse)`
  */
object ApiObjects extends DefaultJsonProtocol {

  case class ApiResponse(action: String, id: String, status: String, message: String)

  implicit object responseFormat extends RootJsonFormat[ApiResponse] {
    def write(response: ApiResponse) = {
      if (response.message.isEmpty) {
        JsObject(
          "action" -> JsString(response.action),
          "id" -> JsString(response.id),
          "status" -> JsString(response.status)
        )

      } else {
        JsObject(
          "action" -> JsString(response.action),
          "id" -> JsString(response.id),
          "status" -> JsString(response.status),
          "message" -> JsString(response.message)
        )
      }
    }

    def read(value: JsValue): ApiResponse = value match {
      case JsObject(fs) =>
        val action: String = fs.get("action").map(stringFromValue).getOrElse("")
        val id: String = fs.get("id").map(stringFromValue).getOrElse("")
        val status: String = fs.get("status").map(stringFromValue).getOrElse("")
        val message: String = fs.get("message").map(stringFromValue).getOrElse("")
        ApiResponse(action, id, status, message)
      case _ => ApiResponse("", "", "", "")
    }
  }

  /** Helper function for json string conversion */
  private def stringFromValue(jsVal: JsValue): String = jsVal match {
    case JsString(str) => str
    case v: JsValue => v.toString
  }
}
