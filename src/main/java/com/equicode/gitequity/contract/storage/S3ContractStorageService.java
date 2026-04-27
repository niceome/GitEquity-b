package com.equicode.gitequity.contract.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3에 PDF를 저장한다 (운영 환경).
 * 활성 프로파일: prod
 *
 * 환경변수:
 *   AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_REGION
 *   S3_BUCKET_NAME
 */
@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class S3ContractStorageService implements ContractStorageService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    @Override
    public String uploadPdf(byte[] pdfBytes, Long contractId) {
        String key = "contracts/contract-%d.pdf".formatted(contractId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/pdf")
                .contentLength((long) pdfBytes.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(pdfBytes));

        String url = "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
        log.info("[S3] uploaded contract PDF: {} ({} bytes)", url, pdfBytes.length);
        return url;
    }
}
