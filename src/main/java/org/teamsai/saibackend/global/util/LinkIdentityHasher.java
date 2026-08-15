package org.teamsai.saibackend.global.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HexFormat;

public final class LinkIdentityHasher {

    private static final String DELIMITER = "|";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public static String hash(String name, LocalDate birthDate, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("명의 해시 처리 실패: link-identity.hash-secret 값이 비어 있습니다.");
        }

        String normalized = normalizeName(name) + DELIMITER + normalizeBirthDate(birthDate);

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hashBytes = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "명의 해시 처리 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e
            );
        }
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return Normalizer.normalize(name.strip(), Normalizer.Form.NFC);
    }

    private static String normalizeBirthDate(LocalDate birthDate) {
        return birthDate == null ? "" : birthDate.toString();
    }
}