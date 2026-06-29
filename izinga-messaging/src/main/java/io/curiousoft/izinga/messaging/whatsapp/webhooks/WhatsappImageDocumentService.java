package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import io.curiousoft.izinga.commons.model.UserProfile;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.usermanagement.userconfig.FieldSpec;
import io.curiousoft.izinga.usermanagement.userconfig.FieldDataType;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfig;
import io.curiousoft.izinga.usermanagement.userconfig.UserConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WhatsappImageDocumentService {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappImageDocumentService.class);

    private final UserProfileRepo userProfileRepo;
    private final UserConfigService userConfigService;
    private final WhatsappMediaDownloader mediaDownloader;
    private final MediaStorageService mediaStorageService;

    public WhatsappImageDocumentService(UserProfileRepo userProfileRepo,
                                        UserConfigService userConfigService,
                                        WhatsappMediaDownloader mediaDownloader,
                                        MediaStorageService mediaStorageService) {
        this.userProfileRepo = userProfileRepo;
        this.userConfigService = userConfigService;
        this.mediaDownloader = mediaDownloader;
        this.mediaStorageService = mediaStorageService;
    }

    public WhatsappImageProcessResult processImageAndTagUser(String from, WhatsappWebhookPayload.Value.Message.Image image) {
        if (image == null) {
            throw new IllegalArgumentException("Image payload is required");
        }

        UserProfile user = findUserByPhonePrefixes(from);
        if (user == null) {
            throw new IllegalArgumentException("No user profile found for WhatsApp sender: " + from);
        }

        UserConfig config = resolveUserConfig(user);
        List<String> documentFieldNames = getDocumentFieldNames(config);
        String tagField = resolveTagField(image.getCaption(), documentFieldNames, user);

        var downloaded = mediaDownloader.downloadImage(image);
        String fileName = buildStorageName(downloaded.mediaId(), downloaded.mimeType());
        URL uploadedUrl = mediaStorageService.upload(fileName, downloaded.bytes());

        if (user.getTag() == null) {
            user.setTag(new HashMap<>());
        }
        user.getTag().put(tagField, uploadedUrl.toString());
        userProfileRepo.save(user);

        LOG.info("Tagged WhatsApp image for userId={} field={} url={}", user.getId(), tagField, uploadedUrl);
        return new WhatsappImageProcessResult(
                downloaded.mediaId(),
                uploadedUrl.toString(),
                tagField,
                downloaded.mimeType(),
                fileName
        );
    }

    private UserConfig resolveUserConfig(UserProfile user) {
        UserConfig config = userConfigService.findAll().stream()
            .filter(item -> item != null && item.getLabel() != null && item.getLabel().equals(user.getDescription()))
                .findFirst()
                .orElse(null);
        if (config == null) {
            throw new IllegalArgumentException("No user config found for service type: " + user.getDescription());
        }
        return config;
    }

    private List<String> getDocumentFieldNames(UserConfig config) {
        List<FieldSpec> fields = new ArrayList<>();
        fields.addAll(config.getMandatoryFields());
        fields.addAll(config.getOptionalFields());
        return fields.stream()
            .filter(field -> field != null && field.getDataType() == FieldDataType.DOCUMENT_URL)
            .map(FieldSpec::getName)
                .toList();
    }

    private String resolveTagField(String caption, List<String> documentFieldNames, UserProfile user) {
        if (documentFieldNames.isEmpty()) {
            throw new IllegalArgumentException("No document fields are configured for service type: " + user.getDescription());
        }

        String captionField = normalizeCaptionField(caption);
        if (captionField != null) {
            for (String fieldName : documentFieldNames) {
                if (fieldName.equalsIgnoreCase(captionField)) {
                    return fieldName;
                }
            }
        }

        for (String fieldName : documentFieldNames) {
            if (user.getTag() == null || user.getTag().get(fieldName) == null || user.getTag().get(fieldName).isBlank()) {
                return fieldName;
            }
        }

        return documentFieldNames.get(0);
    }

    private String normalizeCaptionField(String caption) {
        if (caption == null || caption.isBlank()) {
            return null;
        }

        String trimmed = caption.trim();
        if (trimmed.startsWith("field:")) {
            String extracted = trimmed.substring("field:".length()).trim();
            return extracted.isBlank() ? null : extracted;
        }

        return trimmed.matches("[a-zA-Z][a-zA-Z0-9_]{2,60}") ? trimmed : null;
    }

    private UserProfile findUserByPhonePrefixes(String phone) {
        if (phone == null || phone.length() < 9) {
            return null;
        }
        String last9 = phone.substring(phone.length() - 9);
        String[] prefixes = new String[]{"0", "+27", "27"};
        for (String prefix : prefixes) {
            UserProfile user = userProfileRepo.findByMobileNumber(prefix + last9);
            if (user != null) {
                return user;
            }
        }
        return null;
    }

    private String buildStorageName(String mediaId, String mimeType) {
        String ext = mimeTypeToExtension(mimeType);
        String idPart = mediaId != null && !mediaId.isBlank() ? mediaId : UUID.randomUUID().toString();
        return "whatsapp/" + Instant.now().toEpochMilli() + "_" + idPart + ext;
    }

    private String mimeTypeToExtension(String mimeType) {
        if (mimeType == null) return ".jpg";
        return switch (mimeType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

}
