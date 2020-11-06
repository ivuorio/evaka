package fi.espoo.evaka.attachment

import fi.espoo.evaka.s3.DocumentService
import fi.espoo.evaka.s3.DocumentWrapper
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.db.transaction
import fi.espoo.evaka.shared.domain.BadRequest
import org.jdbi.v3.core.Jdbi
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

val validTypes = listOf(
    "image/jpeg",
    "image/png",
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.oasis.opendocument.text"
)

@RestController
@RequestMapping("/attachments")
class AttachmentsController(
    @Value("\${fi.espoo.voltti.document.bucket.attachment}")
    private val attachmentsBucket: String,
    private val s3Client: DocumentService,
    private val jdbi: Jdbi
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun handleFileUpload(
        user: AuthenticatedUser,
        @RequestPart("file") file: MultipartFile
    ): ResponseEntity<UploadResponse> {
        user.requireOneOfRoles(UserRole.ADMIN)

        val name = file.originalFilename
            ?.takeIf { it.isNotBlank() }
            ?: throw BadRequest("Filename missing")

        val contentType = file.contentType
            ?.also { println(it) }
            ?.takeIf { it in validTypes }
            ?: throw BadRequest("Invalid content type")

        val uri = s3Client.upload(
            attachmentsBucket,
            DocumentWrapper(
                name = file.originalFilename!!,
                path = "/",
                bytes = file.bytes
            ),
            contentType
        ).uri

        val id = jdbi.transaction { it.insertAttachment(uri, name, contentType) }

        return ResponseEntity.ok(
            UploadResponse(
                id = id
            )
        )
    }
}

data class UploadResponse(
    val id: UUID
)
