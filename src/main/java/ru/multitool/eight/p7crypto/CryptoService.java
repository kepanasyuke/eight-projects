package ru.multitool.eight.p7crypto;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.multitool.eight.common.ApiException;

/**
 * Проект 7. Подпись и проверка через JCA/JCE: работа идёт с абстрактным провайдером,
 * поэтому в промышленной среде сюда подставляется КриптоПро JCP/JCSP с ГОСТ-алгоритмами
 * (GOST3410_2012_256 и ГОСТ-хэши) без изменения логики сервиса.
 */
@Service
public class CryptoService {

    private static final Logger LOG = LoggerFactory.getLogger(CryptoService.class);

    private final KeyStore keyStore;
    private final String keyAlias;
    private final String keyPassword;
    private final String signatureAlgorithm;
    private final CertificateInfo cachedInfo;

    public CryptoService(ResourceLoader resourceLoader,
                         @Value("${eight.crypto.keystore:classpath:crypto/demo-keystore.p12}") String keystoreLocation,
                         @Value("${eight.crypto.keystore-password:changeit}") String storePassword,
                         @Value("${eight.crypto.key-alias:eight-demo}") String keyAlias,
                         @Value("${eight.crypto.key-password:changeit}") String keyPassword,
                         @Value("${eight.crypto.signature-algorithm:SHA256withRSA}") String signatureAlgorithm) {
        this.keyAlias = keyAlias;
        this.keyPassword = keyPassword;
        this.signatureAlgorithm = signatureAlgorithm;
        this.keyStore = loadKeyStore(resourceLoader.getResource(keystoreLocation), storePassword);
        this.cachedInfo = readCertificateInfo();
    }

    public SignResult sign(String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        String signatureBase64 = null;
        try {
            Signature signature = Signature.getInstance(this.signatureAlgorithm);
            signature.initSign(privateKey());
            signature.update(data);
            byte[] signed = signature.sign();
            signatureBase64 = Base64.getEncoder().encodeToString(signed);
        } catch (Exception ex) {
            LOG.error("Ошибка подписи", ex);
            throw new ApiException("SIGN_FAILED", "Не удалось сформировать подпись: " + ex.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        SignResult result = new SignResult(this.signatureAlgorithm, signatureBase64, data.length,
                this.cachedInfo.subject(), Instant.now());
        return result;
    }

    public VerifyReport verify(String payload, String signatureBase64) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        String reason = "SIGNATURE_VALID";
        boolean valid = false;
        X509Certificate certificate = certificate();
        try {
            certificate.checkValidity(new Date());
            if (certificate.getNotAfter().toInstant().isBefore(Instant.now())) {
                reason = "CERT_EXPIRED";
            } else {
                Signature signature = Signature.getInstance(this.signatureAlgorithm);
                signature.initVerify(publicKey());
                signature.update(data);
                valid = signature.verify(Base64.getDecoder().decode(signatureBase64));
                if (!valid) {
                    reason = "SIGNATURE_INVALID";
                }
            }
        } catch (java.security.cert.CertificateExpiredException ex) {
            reason = "CERT_EXPIRED";
        } catch (java.security.cert.CertificateNotYetValidException ex) {
            reason = "CERT_NOT_YET_VALID";
        } catch (IllegalArgumentException ex) {
            reason = "SIGNATURE_MALFORMED";
        } catch (Exception ex) {
            LOG.error("Ошибка проверки подписи", ex);
            reason = "VERIFY_FAILED";
        }
        VerifyReport report = new VerifyReport(valid, reason, this.cachedInfo.subject(),
                this.cachedInfo.daysUntilExpiry(), this.signatureAlgorithm);
        return report;
    }

    public TamperReport tamperDemo(String payload) {
        SignResult signed = sign(payload);
        String tamperedPayload = payload + " ";
        VerifyReport tampered = verify(tamperedPayload, signed.signatureBase64());
        VerifyReport original = verify(payload, signed.signatureBase64());
        TamperReport report = new TamperReport(original.valid(), tampered.valid(), tampered.reason(),
                "Изменение подписанных данных на один символ ломает проверку");
        return report;
    }

    public CertificateInfo certificateInfo() {
        return this.cachedInfo;
    }

    public List<String> chain() {
        List<String> chain = new ArrayList<>();
        try {
            Certificate[] certificates = this.keyStore.getCertificateChain(this.keyAlias);
            for (Certificate certificate : certificates) {
                X509Certificate x509 = (X509Certificate) certificate;
                chain.add(x509.getSubjectX500Principal().getName() + " / " + x509.getSigAlgName());
            }
        } catch (Exception ex) {
            throw new ApiException("CHAIN_READ_FAILED", "Не удалось прочитать цепочку сертификатов",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return List.copyOf(chain);
    }

    private PrivateKey privateKey() throws Exception {
        return (PrivateKey) this.keyStore.getKey(this.keyAlias, this.keyPassword.toCharArray());
    }

    private PublicKey publicKey() {
        PublicKey key = null;
        try {
            key = certificate().getPublicKey();
        } catch (Exception ex) {
            throw new ApiException("PUBLIC_KEY_FAILED", "Не удалось получить открытый ключ",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return key;
    }

    private X509Certificate certificate() {
        X509Certificate certificate = null;
        try {
            certificate = (X509Certificate) this.keyStore.getCertificate(this.keyAlias);
        } catch (Exception ex) {
            throw new ApiException("CERTIFICATE_FAILED", "Не удалось прочитать сертификат",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return certificate;
    }

    private CertificateInfo readCertificateInfo() {
        X509Certificate certificate = certificate();
        long days = Duration.between(Instant.now(), certificate.getNotAfter().toInstant()).toDays();
        String fingerprint = fingerprint(certificate);
        CertificateInfo info = new CertificateInfo(
                certificate.getSubjectX500Principal().getName(),
                certificate.getIssuerX500Principal().getName(),
                certificate.getSerialNumber().toString(16),
                certificate.getSigAlgName(),
                certificate.getPublicKey().getAlgorithm(),
                certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant(),
                days,
                chain().size(),
                fingerprint
        );
        return info;
    }

    private String fingerprint(X509Certificate certificate) {
        String fingerprint = null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            fingerprint = HexFormat.of().withDelimiter(":").formatHex(digest).toUpperCase();
        } catch (Exception ex) {
            fingerprint = "unavailable";
        }
        return fingerprint;
    }

    private static KeyStore loadKeyStore(Resource resource, String password) {
        KeyStore keyStore = null;
        try (InputStream input = resource.getInputStream()) {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, password.toCharArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось загрузить хранилище ключей", ex);
        }
        return keyStore;
    }

    public record SignResult(String algorithm, String signatureBase64, int payloadBytes, String certificateSubject,
                             Instant signedAt) {
    }

    public record VerifyReport(boolean valid, String reason, String subject, long daysUntilExpiry,
                               String algorithm) {
    }

    public record TamperReport(boolean originalValid, boolean tamperedValid, String tamperedReason,
                               String comment) {
    }

    public record CertificateInfo(String subject, String issuer, String serialNumber, String signatureAlgorithm,
                                  String keyAlgorithm, Instant notBefore, Instant notAfter, long daysUntilExpiry,
                                  int chainLength, String sha256Fingerprint) {
    }
}
