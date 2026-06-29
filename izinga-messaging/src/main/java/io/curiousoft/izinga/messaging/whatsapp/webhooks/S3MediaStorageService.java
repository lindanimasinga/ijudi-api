package io.curiousoft.izinga.messaging.whatsapp.webhooks;

import io.curiousoft.izinga.documentmanagement.CloudBucketService;
import org.springframework.stereotype.Service;

import java.net.URL;

@Service
public class S3MediaStorageService implements MediaStorageService {

    private final CloudBucketService cloudBucketService;

    public S3MediaStorageService(CloudBucketService cloudBucketService) {
        this.cloudBucketService = cloudBucketService;
    }

    @Override
    public URL upload(String fileName, byte[] bytes) {
        cloudBucketService.putObject(fileName, bytes);
        return cloudBucketService.getUrl(fileName);
    }
}
