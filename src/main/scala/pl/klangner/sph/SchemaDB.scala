package pl.klangner.sph

import java.util.concurrent.ConcurrentMap

import org.mapdb.{DBMaker, Serializer}


class SchemaDB(filePath: String = "") {

  private lazy val db = {
    if (filePath.isEmpty) {
      DBMaker.memoryDB().make()
    } else {
      DBMaker.fileDB(filePath).make()
    }
  }
  private lazy val storage: ConcurrentMap[String, String] = db.hashMap("map", Serializer.STRING, Serializer.STRING).createOrOpen()

  /** Save schema in the database */
  def put(id: String, schema: String): Unit = {
    storage.put(id, schema)
    db.commit()
  }

  /** Get schema with give id. Return None if not found */
  def fetch(id: String): Option[String] = {
    Option(storage.get(id))
  }

  def close(): Unit = {
    db.close()
  }
}
