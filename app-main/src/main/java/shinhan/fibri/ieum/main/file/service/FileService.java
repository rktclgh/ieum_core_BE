package shinhan.fibri.ieum.main.file.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shinhan.fibri.ieum.common.auth.principal.AuthenticatedUser;
import shinhan.fibri.ieum.common.file.domain.File;
import shinhan.fibri.ieum.common.file.repository.FileRepository;
import shinhan.fibri.ieum.main.file.dto.FileCompleteResponse;
import shinhan.fibri.ieum.main.file.dto.FilePresignRequest;
import shinhan.fibri.ieum.main.file.dto.FilePresignResponse;
import shinhan.fibri.ieum.main.file.dto.FileStreamResponse;
import shinhan.fibri.ieum.main.file.exception.FileNotFoundException;
import shinhan.fibri.ieum.main.file.exception.InvalidFileRequestException;
import shinhan.fibri.ieum.main.file.rendition.FileRendition;
import shinhan.fibri.ieum.main.file.rendition.ImageRenditionGenerator;
import shinhan.fibri.ieum.main.file.storage.FileObjectMetadata;
import shinhan.fibri.ieum.main.file.storage.FileStorage;
import shinhan.fibri.ieum.main.file.storage.StoredFileObject;

@Service
public class FileService {

	private final FileRepository fileRepository;
	private final FileStorage storage;
	private final ImageRenditionGenerator renditionGenerator;
	private final FileProperties properties;

	public FileService(
		FileRepository fileRepository,
		FileStorage storage,
		ImageRenditionGenerator renditionGenerator,
		FileProperties properties
	) {
		this.fileRepository = fileRepository;
		this.storage = storage;
		this.renditionGenerator = renditionGenerator;
		this.properties = properties;
	}

	@Transactional
	public FilePresignResponse createPresign(AuthenticatedUser principal, FilePresignRequest request) {
		String contentType = validateOriginContentType(request.contentType());
		Long sizeBytes = validateSizeBytes(request.sizeBytes());
		UUID fileId = UUID.randomUUID();
		String key = FileObjectKeys.tmpOriginKey(
			properties.tmpPrefix(),
			principal.userId(),
			request.purpose(),
			fileId,
			contentType
		);
		File file = fileRepository.save(File.pending(fileId, principal.userId(), key, contentType, sizeBytes));
		URI uploadUrl = storage.createPresignedPutUrl(file.getS3Key(), contentType, sizeBytes, properties.presignTtl());

		return new FilePresignResponse(file.getFileId(), uploadUrl);
	}

	@Transactional
	public FileCompleteResponse complete(AuthenticatedUser principal, UUID fileId) {
		File file = fileRepository.findByFileIdAndUploaderId(fileId, principal.userId())
			.orElseThrow(FileNotFoundException::new);
		if (file.isUploaded()) {
			return new FileCompleteResponse(file.getFileId());
		}

		FileObjectMetadata metadata = storage.head(file.getS3Key());
		String contentType = validateOriginContentType(metadata.contentType());
		Long sizeBytes = validateSizeBytes(metadata.sizeBytes());
		if (!contentType.equals(FileObjectKeys.normalize(file.getContentType()))) {
			throw new InvalidFileRequestException("Uploaded content type does not match presigned content type");
		}

		String tmpKey = file.getS3Key();
		String finalKey = FileObjectKeys.promoteTmpOriginKey(
			properties.tmpPrefix(),
			properties.finalPrefix(),
			tmpKey
		);
		storage.copy(tmpKey, finalKey);
		StoredFileObject origin = storage.get(finalKey);
		for (FileRendition rendition : renditionGenerator.generate(origin, properties)) {
			storage.put(
				FileObjectKeys.variantKey(finalKey, rendition.variant()),
				rendition.contentType(),
				rendition.bytes()
			);
		}
		file.promoteKey(finalKey);
		file.markUploaded(OffsetDateTime.now(), contentType, sizeBytes);
		fileRepository.save(file);
		storage.delete(tmpKey);

		return new FileCompleteResponse(file.getFileId());
	}

	@Transactional(readOnly = true)
	public FileStreamResponse stream(AuthenticatedUser principal, UUID fileId, String variant) {
		File file = fileRepository.findById(fileId)
			.filter(File::isUploaded)
			.orElseThrow(FileNotFoundException::new);
		FileVariant fileVariant = FileVariant.from(variant);
		String key = FileObjectKeys.variantKey(file.getS3Key(), fileVariant);
		StoredFileObject object = storage.get(key);
		validateStreamContentType(object.contentType());

		return new FileStreamResponse(object.contentType(), object.sizeBytes(), object.bytes());
	}

	private String validateOriginContentType(String contentType) {
		String normalized = FileObjectKeys.normalize(contentType);
		if (!FileObjectKeys.isSupportedOriginContentType(normalized)) {
			throw new InvalidFileRequestException("Only jpeg and png images are supported");
		}
		return normalized;
	}

	private Long validateSizeBytes(Long sizeBytes) {
		if (sizeBytes == null || sizeBytes <= 0) {
			throw new InvalidFileRequestException("sizeBytes must be positive");
		}
		if (sizeBytes > properties.maxSizeBytes()) {
			throw new InvalidFileRequestException("File is too large");
		}
		return sizeBytes;
	}

	private void validateStreamContentType(String contentType) {
		String normalized = FileObjectKeys.normalize(contentType);
		if (!"image/jpeg".equals(normalized) && !"image/png".equals(normalized) && !"image/webp".equals(normalized)) {
			throw new InvalidFileRequestException("Only jpeg, png, and webp images can be streamed");
		}
	}
}
