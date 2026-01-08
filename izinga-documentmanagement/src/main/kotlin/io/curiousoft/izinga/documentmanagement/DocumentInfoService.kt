package io.curiousoft.izinga.documentmanagement

import com.fasterxml.jackson.databind.JsonNode
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

    fun analyzeImageWithResponsesApi(imageUrl: String, docType: String, jsonSchemaNode: JsonNode?): JsonNode? {
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
                    "name" to docType,
                    "schema" to jsonSchemaNode
                )
            )
        )

        val entity = HttpEntity(requestBody, headers)
        return restTemplate.postForObject(apiUrl, entity, ResponsesData::class.java)
            ?.let {
                it.output.firstOrNull()?.content?.firstOrNull()?.text
                    ?.let { text -> jacksonObjectMapper().readTree(text) }
            }
    }

    fun createImage(prompt: String, n: Int = 1, size: String = "1024x1024"): List<String> {
        val apiUrl = "https://api.openai.com/v1/images/generations"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(openAiApiKey)
        }

        val requestBody = mapOf(/*
            "model" to "gpt-image-1.5",*/
            "prompt" to prompt,
            "n" to n,
            "size" to size
        )

        val entity = HttpEntity(requestBody, headers)
        val response = restTemplate.postForObject(apiUrl, entity, Map::class.java)
        val data = response?.get("data") as? List<Map<String, String>>
        return data?.mapNotNull { it["url"] } ?: emptyList()
    }
}