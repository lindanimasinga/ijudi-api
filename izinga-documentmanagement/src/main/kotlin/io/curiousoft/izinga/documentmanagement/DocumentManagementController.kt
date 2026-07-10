package io.curiousoft.izinga.documentmanagement

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.victools.jsonschema.generator.OptionPreset
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import io.curiousoft.izinga.documentmanagement.type.DocMetadata
import io.curiousoft.izinga.documentmanagement.type.DocTypesEnum
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import java.net.URL
import java.util.*
import kotlin.reflect.KClass

@RestController
@RequestMapping("/document")
class DocumentManagementController(private val cloudBucketService: CloudBucketService, private val documentInfoService: DocumentInfoService) {

    private val logger = LoggerFactory.getLogger(DocumentManagementController::class.java)

    @PostMapping
    fun uploadFile(@RequestParam("file") file: MultipartFile,
                   @RequestParam("metadata") metadata: Boolean = false,
                   @RequestParam("docData", required = false) docMetadataStr: String?,
                   @RequestParam("docType", required = false) documentType: DocTypesEnum?): Map<String, Any?> {
        val fileName = UUID.randomUUID().toString() + "_" + file.originalFilename
        val docMetadata: DocMetadata? = if (docMetadataStr != null) {
            try {
                jacksonObjectMapper().readValue(docMetadataStr, DocMetadata::class.java)
            } catch (e: Exception) {
                // Malformed docData must not fail the upload — skip extraction, same as if
                // no docData had been sent at all.
                logger.warn("Malformed docData param; skipping extraction for file={}", fileName, e)
                null
            }
        } else null

        cloudBucketService.putObject(fileName, file.bytes)
        val fileUrl: URL = cloudBucketService.getUrl(fileName)
        val baseResult = mapOf("fileName" to fileName, "url" to fileUrl.toString())
        if (metadata && documentType != null) {
            return try {
                val extracted = documentInfoService.analyzeImageWithResponsesApi(
                    fileUrl.toString(),
                    documentType.klass.simpleName!!,
                    generateJsonSchema(documentType.klass),
                )
                mapOf("fileName" to fileName, "url" to fileUrl.toString(), "metadata" to extracted)
            } catch (e: Exception) {
                // Extraction failure (network error, rate limit, malformed response) must
                // never surface as a 500 — file was uploaded successfully, return URL only.
                logger.warn("Document extraction failed (docType={}) for file={}; returning URL only", documentType, fileName, e)
                baseResult
            }
        } else if (metadata && docMetadata != null) {
            return try {
                val extracted = documentInfoService.analyzeImageWithResponsesApi(
                    fileUrl.toString(),
                    docMetadata.name,
                    docMetadata.toJsonSchema(),
                )
                // Hidden fields were sent to the vision model (they're still part of the
                // schema) but must never reach the frontend — strip them before returning.
                // Any response shape other than an ObjectNode (or null) is treated as a
                // failure rather than passed through, so a hidden field can never leak
                // through an unexpected extraction result.
                val safeMetadata = when {
                    extracted == null -> null
                    extracted is ObjectNode -> extracted.also { node ->
                        docMetadata.hiddenFieldNames().forEach { node.remove(it) }
                    }
                    else -> {
                        logger.warn(
                            "Unexpected extraction response shape ({}) for docMetadata={}; discarding to avoid leaking hidden fields",
                            extracted.nodeType, docMetadata.name
                        )
                        null
                    }
                }
                mapOf("fileName" to fileName, "url" to fileUrl.toString(), "metadata" to safeMetadata)
            } catch (e: Exception) {
                logger.warn("Document extraction failed (docMetadata={}) for file={}; returning URL only", docMetadata.name, fileName, e)
                baseResult
            }
        }
        return baseResult
    }

    @DeleteMapping
    fun deleteFile(@RequestParam("fileName") fileName: String): Map<String, String> {
        cloudBucketService.deleteObject(fileName)
        return mapOf("message" to "File $fileName deleted successfully")
    }

    fun generateJsonSchema(clazz: KClass<*>): ObjectNode? {
        val mapper: ObjectMapper = jacksonObjectMapper()
        val configBuilder = SchemaGeneratorConfigBuilder(
            mapper,
            SchemaVersion.DRAFT_2020_12,
            OptionPreset.PLAIN_JSON)
        val generator = SchemaGenerator(configBuilder.build())
        return generator.generateSchema(clazz.java)
            ?.also {
                it.put("additionalProperties", false)
                it.putPOJO("required", it.get("properties")?.fieldNames()?.asSequence()?.toList())
            }
    }
}
