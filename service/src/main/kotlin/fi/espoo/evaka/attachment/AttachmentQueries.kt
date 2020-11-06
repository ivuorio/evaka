package fi.espoo.evaka.attachment

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo
import java.net.URI
import java.util.UUID

fun Handle.insertAttachment(uri: URI, name: String, contentType: String): UUID {
    // language=sql
    val sql =
        """
        INSERT INTO attachment (uri, name, content_type) 
        VALUES (:uri, :name, :contentType)
        RETURNING id
        """.trimIndent()
    return this.createUpdate(sql)
        .bind("uri", uri)
        .bind("name", name)
        .bind("contentType", contentType)
        .executeAndReturnGeneratedKeys()
        .mapTo<UUID>()
        .first()
}
