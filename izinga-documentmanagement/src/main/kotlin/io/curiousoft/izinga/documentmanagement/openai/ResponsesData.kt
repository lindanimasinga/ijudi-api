package io.curiousoft.izinga.documentmanagement.openai

data class ResponsesData(
    val id: String,
    val `object`: String,
    val created_at: Long,
    val status: String,
    val background: Boolean,
    val billing: Billing,
    val error: Any?,
    val incomplete_details: Any?,
    val instructions: Any?,
    val max_output_tokens: Any?,
    val max_tool_calls: Any?,
    val model: String,
    val output: List<Output>,
    val parallel_tool_calls: Boolean,
    val previous_response_id: Any?,
    val prompt_cache_key: Any?,
    val reasoning: Reasoning,
    val safety_identifier: Any?,
    val service_tier: String,
    val store: Boolean,
    val temperature: Double,
    val text: Text,
    val tool_choice: String,
    val tools: List<Any>,
    val top_logprobs: Int,
    val top_p: Double,
    val truncation: String,
    val usage: Usage,
    val user: Any?,
    val metadata: Map<String, Any>
)

data class Billing(
    val payer: String
)

data class Output(
    val id: String,
    val type: String,
    val status: String,
    val content: List<Content>,
    val role: String
)

data class Content(
    val type: String,
    val annotations: List<Any>?,
    val logprobs: List<Any>?,
    val text: String?
)

data class Reasoning(
    val effort: Any?,
    val summary: Any?
)

data class Text(
    val format: Format,
    val verbosity: String
)

data class Format(
    val type: String,
    val description: Any?,
    val name: String,
    val schema: Schema,
    val strict: Boolean
)

data class Schema(
    val type: String,
    val properties: Map<String, Property>,
    val additionalProperties: Boolean,
    val required: List<String>
)

data class Property(
    val type: String
)

data class Usage(
    val input_tokens: Int,
    val input_tokens_details: InputTokensDetails,
    val output_tokens: Int,
    val output_tokens_details: OutputTokensDetails,
    val total_tokens: Int
)

data class InputTokensDetails(
    val cached_tokens: Int
)

data class OutputTokensDetails(
    val reasoning_tokens: Int
)