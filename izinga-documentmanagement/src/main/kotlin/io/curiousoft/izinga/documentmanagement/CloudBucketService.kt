package io.curiousoft.izinga.documentmanagement

import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URL

@Service
class CloudBucketService(private val s3Client: S3Client) {

    private val bucket = "izinga-aut"

    fun putObject(fileName: String, bytes: ByteArray) {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(fileName)
            .build()
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes))
    }

    fun getUrl(key: String): URL {
        return s3Client.utilities().getUrl { it.bucket(bucket).key(key) }
    }

    fun deleteObject(fileName: String) {
        val deleteObjectRequest = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(fileName)
            .build()
        s3Client.deleteObject(deleteObjectRequest)
    }
}

