package shinhan.fibri.ieum.main.file.storage;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

public class S3FileStorage implements FileStorage {

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final String bucket;

	public S3FileStorage(S3Client s3Client, S3Presigner s3Presigner, String bucket) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.bucket = bucket;
	}

	@Override
	public URI createPresignedPutUrl(String key, String contentType, Long sizeBytes, Duration ttl) {
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.contentType(contentType)
			.contentLength(sizeBytes)
			.build();
		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
			.signatureDuration(ttl)
			.putObjectRequest(putObjectRequest)
			.build();

		return URI.create(s3Presigner.presignPutObject(presignRequest).url().toString());
	}

	@Override
	public FileObjectMetadata head(String key) {
		HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.build());
		return new FileObjectMetadata(response.contentType(), response.contentLength());
	}

	@Override
	public void copy(String sourceKey, String destinationKey) {
		s3Client.copyObject(CopyObjectRequest.builder()
			.sourceBucket(bucket)
			.sourceKey(sourceKey)
			.destinationBucket(bucket)
			.destinationKey(destinationKey)
			.build());
	}

	@Override
	public StoredFileObject get(String key) {
		ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.build());
		return new StoredFileObject(
			key,
			response.response().contentType(),
			response.response().contentLength(),
			response.asByteArray()
		);
	}

	@Override
	public void put(String key, String contentType, byte[] bytes) {
		s3Client.putObject(
			PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType)
				.contentLength((long) bytes.length)
				.build(),
			RequestBody.fromBytes(bytes)
		);
	}

	@Override
	public void delete(String key) {
		s3Client.deleteObject(DeleteObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.build());
	}
}
