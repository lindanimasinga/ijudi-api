package io.curiousoft.izinga.documentmanagement

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.victools.jsonschema.generator.OptionPreset
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import io.curiousoft.izinga.documentmanagement.openai.ResponsesData
import io.curiousoft.izinga.documentmanagement.type.DocType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.*
import org.springframework.beans.factory.annotation.Value
import kotlin.reflect.KClass

@Service
class DocumentInfoService(
    private val restTemplate: RestTemplate = RestTemplate(),
    @Value("\${openai.api.key}") private val openAiApiKey: String
) {

    fun analyzeImageWithResponsesApi(imageUrl: String, docType: KClass<DocType>): DocType? {
        val apiUrl = "https://api.openai.com/v1/responses"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(openAiApiKey)
        }

        val requestBody = mapOf(
            "model" to "gpt-4.1",
            "input" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "input_text",
                            "text" to "what is in this image, return data in json?"
                        ),
                        mapOf(
                            "type" to "input_image",
                            "image_url" to imageUrl
                        )
                    )
                )
            ),
            "text" to mapOf(
                "format" to mapOf(
                    "type" to "json_schema",
                    "name" to docType.simpleName,
                    "schema" to generateJsonSchema(docType)
                )
            )
        )

        val entity = HttpEntity(requestBody, headers)
        return restTemplate.postForObject(apiUrl, entity, ResponsesData::class.java)
            ?.let {
                it.output.firstOrNull()?.content?.firstOrNull()?.text
                    ?.let { text -> jacksonObjectMapper().readValue(text, docType.java) }
            }
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