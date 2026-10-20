package eu.uniche.iguide.asset.infrastructure;

import eu.uniche.iguide.asset.application.AssetStorage;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * The only {@link AssetStorage}: any S3-compatible store, in every environment ({@code 10} section 7).
 * Path-style addressing, because every S3-compatible store supports it while virtual-hosted addressing needs
 * per-bucket DNS that local and self-hosted stores usually lack. Signed URLs name the public endpoint when one
 * is configured, since a browser may reach the store under another host than this application does
 * (DECISIONS.md D-025).
 */
@Component
public class S3AssetStorage implements AssetStorage, DisposableBean {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3AssetStorage(@Value("${iguide.assets.s3.endpoint}") String endpoint,
                          @Value("${iguide.assets.s3.region}") String region,
                          @Value("${iguide.assets.s3.bucket}") String bucket,
                          @Value("${iguide.assets.s3.access-key}") String accessKey,
                          @Value("${iguide.assets.s3.secret-key}") String secretKey,
                          @Value("${iguide.assets.s3.public-endpoint:}") String publicEndpoint) {
        requireSet("iguide.assets.s3.endpoint", endpoint);
        requireSet("iguide.assets.s3.region", region);
        requireSet("iguide.assets.s3.bucket", bucket);
        requireSet("iguide.assets.s3.access-key", accessKey);
        requireSet("iguide.assets.s3.secret-key", secretKey);
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        String signingEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(signingEndpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.bucket = bucket;
        // Fail at startup, not on the first upload: a missing bucket or wrong credentials surface here.
        client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    }

    private static void requireSet(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is not set; asset storage needs an S3-compatible bucket.");
        }
    }

    @Override
    public String providerName() {
        return "S3";
    }

    @Override
    public void store(String key, InputStream data, long sizeBytes, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                .contentLength(sizeBytes).build(), RequestBody.fromInputStream(data, sizeBytes));
    }

    @Override
    public URI signedReadUrl(String key, Duration validity) {
        return presign(GetObjectRequest.builder().bucket(bucket).key(key).build(), validity);
    }

    @Override
    public URI signedDownloadUrl(String key, Duration validity, String filename) {
        String ascii = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return presign(GetObjectRequest.builder().bucket(bucket).key(key)
                .responseContentDisposition("attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded)
                .build(), validity);
    }

    private URI presign(GetObjectRequest request, Duration validity) {
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(validity)
                    .getObjectRequest(request).build()).url().toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("The object store returned an unusable URL", e);
        }
    }

    @Override
    public void destroy() {
        presigner.close();
        client.close();
    }
}
