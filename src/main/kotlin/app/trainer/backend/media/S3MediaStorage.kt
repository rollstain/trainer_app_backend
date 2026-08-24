package app.trainer.backend.media

import java.net.URI
import java.time.Duration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

class S3MediaStorage(private val properties: MediaProperties) : MediaStorage {

    private val credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
    )

    private val presigner: S3Presigner = S3Presigner.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(credentials)
        .build()

    private val client: S3Client = S3Client.builder()
        .endpointOverride(URI.create(properties.endpoint))
        .region(Region.of(properties.region))
        .credentialsProvider(credentials)
        .build()

    override fun presignUpload(
        storageKey: String,
        contentType: String,
        lifetime: Duration,
    ): PresignedUpload {
        val putRequest = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(storageKey)
            .contentType(contentType)
            .build()
        val presigned = presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(lifetime)
                .putObjectRequest(putRequest)
                .build()
        )
        return PresignedUpload(url = presigned.url().toString(), storageKey = storageKey)
    }

    override fun presignDownload(storageKey: String, lifetime: Duration): String {
        val getRequest = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(storageKey)
            .build()
        val presigned = presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(lifetime)
                .getObjectRequest(getRequest)
                .build()
        )
        return presigned.url().toString()
    }

    override fun head(storageKey: String): StoredObject? {
        val response = runCatching {
            client.headObject(
                HeadObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(storageKey)
                    .build()
            )
        }.getOrElse { failure ->
            if (failure is NoSuchKeyException) return null
            throw failure
        }
        return StoredObject(
            sizeBytes = response.contentLength() ?: 0,
            contentType = response.contentType(),
        )
    }

    override fun delete(storageKey: String) {
        client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.bucket)
                .key(storageKey)
                .build()
        )
    }
}
