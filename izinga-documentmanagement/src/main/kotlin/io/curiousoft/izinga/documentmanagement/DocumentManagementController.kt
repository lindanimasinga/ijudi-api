package io.curiousoft.izinga.documentmanagement

import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import java.net.URL
import java.util.*

@RestController
@RequestMapping("/document")
class DocumentManagementController(private val s3Client: S3Client) {

    private val bucketName = "izinga-aut"

    @PostMapping
    fun uploadFile(@RequestParam("file") file: MultipartFile): Map<String, String> {
        val fileName = UUID.randomUUID().toString() + "_" + file.originalFilename
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(fileName)
            .build()
        s3Client.putObject(putObjectRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(file.bytes))

        val fileUrl: URL = s3Client.utilities().getUrl { it.bucket(bucketName).key(fileName) }
        return mapOf("fileName" to fileName, "url" to fileUrl.toString())
    }

    @DeleteMapping
    fun deleteFile(@RequestParam("fileName") fileName: String): Map<String, String> {
        val deleteObjectRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(fileName)
            .build()
        s3Client.deleteObject(deleteObjectRequest)
        return mapOf("message" to "File $fileName deleted successfully")
    }
}
