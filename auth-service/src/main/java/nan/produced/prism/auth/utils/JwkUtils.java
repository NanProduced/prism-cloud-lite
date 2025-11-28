package nan.produced.prism.auth.utils;

import com.nimbusds.jose.jwk.RSAKey;
import nan.produced.prism.auth.security.SecurityProps;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

public class JwkUtils {

    private JwkUtils() {
        throw new UnsupportedOperationException();
    }

    public static final String DEFAULT_JWK_ALGORITHM = "RSA";

    public static RSAKey generateRsa() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    private static KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(DEFAULT_JWK_ALGORITHM);
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return keyPair;
    }

    public static RSAKey convertRsaKey(SecurityProps properties) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        RSAKey rsaKey;
        if (properties == null || properties.getJwt().getRsaPublicKey() == null || properties.getJwt().getRsaPrivateKey() == null) {
            rsaKey = generateRsa();
        } else {
            rsaKey = new RSAKey.Builder(convertRsaPublicKey(properties.getJwt().getRsaPublicKey()))
                    .privateKey(convertRsaPrivateKey(properties.getJwt().getRsaPrivateKey()))
                    .build();
        }
        return rsaKey;
    }

    public static RSAPublicKey convertRsaPublicKey(Resource resource) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        String resourceText = readResourceText(resource);
        if (resourceText == null) {
            throw new IOException("Public key resource is null or does not exist");
        }
        return (RSAPublicKey) KeyFactory.getInstance(DEFAULT_JWK_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(getPublicKeySpec(resourceText)));
    }

    public static RSAPrivateKey convertRsaPrivateKey(Resource resource) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
        String resourceText = readResourceText(resource);
        if (resourceText == null) {
            throw new IOException("Private key resource is null or does not exist");
        }
        return (RSAPrivateKey) KeyFactory.getInstance(DEFAULT_JWK_ALGORITHM)
                .generatePrivate(new PKCS8EncodedKeySpec(getPrivateKeySpec(resourceText)));
    }

    /**
     * 读取资源文件
     * @param resourceLocation 资源文件
     * @return 文件内容
     * @throws IOException 读取异常
     */
    public static String readResourceText(Resource resourceLocation) throws IOException {
        if (resourceLocation == null || !resourceLocation.exists()) {
            return null;
        }
        try (InputStream inputStream = resourceLocation.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取公钥
     * @param keyText 公钥文本
     * @return 公钥
     */
    private static byte[] getPublicKeySpec(String keyText) {
        keyText = keyText
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "");
        return Base64.getMimeDecoder().decode(keyText);
    }

    /**
     * 获取私钥
     * @param keyText 私钥文本
     * @return 私钥
     */
    private static byte[] getPrivateKeySpec(String keyText) {
        keyText = keyText
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");
        return Base64.getMimeDecoder().decode(keyText);
    }
}
