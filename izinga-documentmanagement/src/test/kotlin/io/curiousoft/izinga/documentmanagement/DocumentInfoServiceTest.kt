import com.fasterxml.jackson.databind.ObjectMapper
import io.curiousoft.izinga.documentmanagement.DocumentInfoService
import io.curiousoft.izinga.documentmanagement.type.DocType
import io.curiousoft.izinga.documentmanagement.type.LicenseDisc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Disabled("Requires valid OpenAI API key and network access")
class DocumentInfoServiceTest {

    private val restTemplate = RestTemplate()
    private val apiKey = ""
    private val service = DocumentInfoService(restTemplate, apiKey)

/*    @Test
    fun `analyzeImageWithResponsesApi returns response map`() {
        val imageUrl = "https://thupello.co.za/wp-content/uploads/2021/10/ShuttleDirect_1446193980.jpg"
        val expectedResponse = mapOf("result" to "some text")
        val apiUrl = "https://api.openai.com/v1/responses"

        val result = service.analyzeImageWithResponsesApi(imageUrl, LicenseDisc::class as KClass<DocType>)
        //print all fields of results
        println(ObjectMapper().writeValueAsString(result))
        assertEquals(expectedResponse, result)
    }*/

    @Test
    fun `createImage returns urls when external API returns data`() {
        val urls = service.createImage("create an image of a stove", 2, "1024x1024")
        //print the urls
        println(urls)
        assertEquals(2, urls.size)
    }
}