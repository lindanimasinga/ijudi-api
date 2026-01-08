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

    @PostMapping
    fun uploadFile(@RequestParam("file") file: MultipartFile,
                   @RequestParam("metadata") metadata: Boolean = false,
                   @RequestParam("docData", required = false) docMetadataStr: String?,
                   @RequestParam("docType", required = false) documentType: DocTypesEnum?): Map<String, Any?> {
        val fileName = UUID.randomUUID().toString() + "_" + file.originalFilename
        var docMetadata : DocMetadata? = null
        if (docMetadataStr != null) {
            val mapper: ObjectMapper = jacksonObjectMapper()
            docMetadata = mapper.readValue(docMetadataStr, DocMetadata::class.java)
        }

        cloudBucketService.putObject(fileName, file.bytes)
        val fileUrl: URL = cloudBucketService.getUrl(fileName)
        if (metadata && documentType != null) {
            val metadata = documentInfoService.analyzeImageWithResponsesApi(
                fileUrl.toString(),
                documentType.klass.simpleName!!,
                generateJsonSchema(documentType.klass),
            )
            return mapOf("fileName" to fileName,
                "url" to fileUrl.toString(),
                "metadata" to metadata)
        } else if (metadata && docMetadata != null) {
            val metadata = documentInfoService.analyzeImageWithResponsesApi(
                fileUrl.toString(),
                docMetadata.name,
                docMetadata.toJsonSchema(),
            )
            return mapOf("fileName" to fileName,
                "url" to fileUrl.toString(),
                "metadata" to metadata)
        }
        return mapOf("fileName" to fileName, "url" to fileUrl.toString())
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
