package com.climb.api.service;

import com.climb.api.config.CloudflareR2Properties;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class CloudflareR2ArquivoStorageService {

    private final CloudflareR2Properties properties;
    private final ArquivoValidationService validationService;

    public CloudflareR2ArquivoStorageService(CloudflareR2Properties properties,
                                             ArquivoValidationService validationService) {
        this.properties = properties;
        this.validationService = validationService;
    }

    public ArquivoUploadResponseDTO salvar(MultipartFile arquivo, String prefixo) {
        validarConfiguracao();
        ArquivoValidado validado = validationService.validar(arquivo);
        String chave = criarChave(prefixo, validado.nomeOriginal());
        String bucket = resolverBucket();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(chave)
                .contentType(validado.contentType())
                .contentLength(validado.tamanho())
                .build();

        try (S3Client client = criarCliente()) {
            client.putObject(request, RequestBody.fromBytes(validado.conteudo()));
        }

        return new ArquivoUploadResponseDTO(
                validado.nomeOriginal(),
                validado.contentType(),
                validado.tamanho(),
                chave,
                montarUrl(chave)
        );
    }

    public String gerarUrlTemporariaDownload(String urlOuChave) {
        validarConfiguracao();

        String chave = extrairChave(urlOuChave);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(resolverBucket())
                .key(chave)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(request)
                .build();

        try (S3Presigner presigner = criarPresigner()) {
            return presigner.presignGetObject(presignRequest).url().toString();
        }
    }

    public byte[] baixar(String urlOuChave) {
        validarConfiguracao();

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(resolverBucket())
                .key(extrairChave(urlOuChave))
                .build();

        try (S3Client client = criarCliente()) {
            ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(request);
            return response.asByteArray();
        }
    }

    private S3Client criarCliente() {
        return S3Client.builder()
                .endpointOverride(resolverEndpoint())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
                ))
                .build();
    }

    private S3Presigner criarPresigner() {
        return S3Presigner.builder()
                .endpointOverride(resolverEndpoint())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
                ))
                .build();
    }

    private void validarConfiguracao() {
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getAccessKey())
                || !StringUtils.hasText(properties.getSecretKey())
                || !StringUtils.hasText(resolverBucket())) {
            throw new IllegalStateException("Configuração do Cloudflare R2 incompleta.");
        }
    }

    private URI resolverEndpoint() {
        URI endpoint = URI.create(properties.getEndpoint());
        String path = endpoint.getPath();
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return endpoint;
        }

        return URI.create("%s://%s".formatted(endpoint.getScheme(), endpoint.getRawAuthority()));
    }

    private String resolverBucket() {
        if (StringUtils.hasText(properties.getBucket())) {
            return properties.getBucket();
        }

        URI endpoint = URI.create(properties.getEndpoint());
        String path = endpoint.getPath();
        if (!StringUtils.hasText(path)) {
            return "";
        }
        return path.replaceFirst("^/+", "").split("/")[0];
    }

    private String criarChave(String prefixo, String nomeOriginal) {
        String prefixoSeguro = StringUtils.hasText(prefixo)
                ? prefixo.replaceAll("[^a-zA-Z0-9/_-]", "_")
                : "arquivos";

        String data = LocalDate.now().toString();
        return "%s/%s/%s_%s".formatted(prefixoSeguro, data, UUID.randomUUID(), nomeOriginal);
    }

    private String montarUrl(String chave) {
        if (!StringUtils.hasText(properties.getPublicUrl())) {
            return chave;
        }
        return properties.getPublicUrl().replaceAll("/+$", "") + "/" + chave;
    }

    private String extrairChave(String urlOuChave) {
        if (!StringUtils.hasText(urlOuChave)) {
            throw new IllegalArgumentException("Arquivo da proposta não encontrado.");
        }

        String valor = urlOuChave.trim();
        if (!valor.startsWith("http://") && !valor.startsWith("https://")) {
            return valor.replaceFirst("^/+", "");
        }

        String path = URI.create(valor).getPath().replaceFirst("^/+", "");
        String bucket = resolverBucket();
        if (StringUtils.hasText(bucket) && path.startsWith(bucket + "/")) {
            return path.substring(bucket.length() + 1);
        }

        return path;
    }
}
