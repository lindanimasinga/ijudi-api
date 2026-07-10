package io.curiousoft.izinga.documentmanagement

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import io.curiousoft.izinga.documentmanagement.type.DocTypesEnum
import io.curiousoft.izinga.documentmanagement.type.FieldDataType
import io.curiousoft.izinga.documentmanagement.type.FieldSpec
import io.curiousoft.izinga.documentmanagement.type.DocMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile
import java.net.URL

@ExtendWith(MockitoExtension::class)
class DocumentManagementControllerTest {

    @Mock
    lateinit var cloudBucketService: CloudBucketService

    @Mock
    lateinit var documentInfoService: DocumentInfoService

    @InjectMocks
    lateinit var controller: DocumentManagementController

    private val dummyFile = MockMultipartFile("file", "test.jpg", "image/jpeg", "bytes".toByteArray())
    private val dummyUrl  = URL("https://s3.example.com/test.jpg")

    private fun objectNodeOf(vararg pairs: Pair<String, String>): ObjectNode =
        JsonNodeFactory.instance.objectNode().also { node -> pairs.forEach { (k, v) -> node.put(k, v) } }

    // -----------------------------------------------------------------------
    // (a) upload with metadata=true + docMetadata — hidden fields are stripped
    // -----------------------------------------------------------------------
    @Test
    fun `uploadFile with docMetadata strips hidden fields from extraction response`() {
        val extractedNode = objectNodeOf(
            "firstName"   to "Jane",
            "surname"     to "Doe",
            "idNumber"    to "9001010000000",
            "dateOfBirth" to "1990-01-01",
        )
        `when`(cloudBucketService.getUrl(anyString())).thenReturn(dummyUrl)
        `when`(documentInfoService.analyzeImageWithResponsesApi(anyString(), anyString(), any()))
            .thenReturn(extractedNode)

        // Inline docData JSON with two hidden fields
        val docDataJson = """{"name":"test_doc","label":"Test Document",
            "mandatoryFields":[{"name":"firstName","label":"First Name","dataType":"STRING"},
            {"name":"surname","label":"Surname","dataType":"STRING"}],
            "optionalFields":[],
            "hiddenFields":[{"name":"idNumber","label":"ID Number","dataType":"STRING"},
            {"name":"dateOfBirth","label":"Date of Birth","dataType":"DATE"}]}"""

        val result = controller.uploadFile(dummyFile, metadata = true, docMetadataStr = docDataJson, documentType = null)

        @Suppress("UNCHECKED_CAST")
        val returnedMetadata = result["metadata"] as ObjectNode
        assertEquals("Jane", returnedMetadata.get("firstName")?.asText())
        assertEquals("Doe",  returnedMetadata.get("surname")?.asText())
        assertNull(returnedMetadata.get("idNumber"),    "idNumber must be stripped from response")
        assertNull(returnedMetadata.get("dateOfBirth"), "dateOfBirth must be stripped from response")
    }

    // -----------------------------------------------------------------------
    // (b) upload with metadata=false returns only {fileName, url}
    // -----------------------------------------------------------------------
    @Test
    fun `uploadFile with metadata=false returns only fileName and url`() {
        `when`(cloudBucketService.getUrl(anyString())).thenReturn(dummyUrl)

        val result = controller.uploadFile(dummyFile, metadata = false, docMetadataStr = null, documentType = null)

        assertEquals(2, result.size, "Result must contain exactly fileName and url")
        assertEquals(dummyUrl.toString(), result["url"])
    }

    // -----------------------------------------------------------------------
    // (c1) docType path: extraction throwing must not propagate a 500
    // -----------------------------------------------------------------------
    @Test
    fun `uploadFile with docType when extraction throws returns fileName and url only`() {
        `when`(cloudBucketService.getUrl(anyString())).thenReturn(dummyUrl)
        `when`(documentInfoService.analyzeImageWithResponsesApi(anyString(), anyString(), any()))
            .thenThrow(RuntimeException("OpenAI rate limit"))

        val result = controller.uploadFile(dummyFile, metadata = true, docMetadataStr = null, documentType = DocTypesEnum.DRIVER_ID)

        assertEquals(2, result.size, "Should fall back to {fileName, url} on extraction failure")
        assertEquals(dummyUrl.toString(), result["url"])
    }

    // -----------------------------------------------------------------------
    // (c2) docMetadata path: extraction throwing must not propagate a 500
    // -----------------------------------------------------------------------
    @Test
    fun `uploadFile with docMetadata when extraction throws returns fileName and url only`() {
        `when`(cloudBucketService.getUrl(anyString())).thenReturn(dummyUrl)
        `when`(documentInfoService.analyzeImageWithResponsesApi(anyString(), anyString(), any()))
            .thenThrow(RuntimeException("Network error"))

        val docDataJson = """{"name":"test_doc","label":"Test",
            "mandatoryFields":[],"optionalFields":[],"hiddenFields":[]}"""

        val result = controller.uploadFile(dummyFile, metadata = true, docMetadataStr = docDataJson, documentType = null)

        assertEquals(2, result.size, "Should fall back to {fileName, url} on extraction failure")
        assertEquals(dummyUrl.toString(), result["url"])
    }

    // -----------------------------------------------------------------------
    // (d) DocMetadata.hiddenFieldNames() — focused unit test for the primitive
    // -----------------------------------------------------------------------
    @Test
    fun `DocMetadata hiddenFieldNames returns only hidden field names`() {
        val docMetadata = DocMetadata(
            name = "test_doc",
            label = "Test",
            mandatoryFields = listOf(FieldSpec("firstName", "First Name", FieldDataType.STRING)),
            optionalFields  = listOf(FieldSpec("surname",   "Surname",    FieldDataType.STRING)),
            hiddenFields    = listOf(
                FieldSpec("idNumber",    "ID Number",     FieldDataType.STRING),
                FieldSpec("dateOfBirth", "Date of Birth", FieldDataType.DATE),
            ),
        )

        val hidden = docMetadata.hiddenFieldNames()

        assertEquals(setOf("idNumber", "dateOfBirth"), hidden)
        assert("firstName" !in hidden) { "visible mandatory field must not appear in hiddenFieldNames" }
        assert("surname"   !in hidden) { "visible optional field must not appear in hiddenFieldNames" }
    }
}
