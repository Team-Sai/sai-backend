package org.teamsai.saibackend.global.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public final class LinkIdentityHasher {

    private static final String DELIMITER = "|";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private LinkIdentityHasher() {
    }

    public static String hash(String name, LocalDate birthDate, String secret) {
        String normalized = normalizeName(name) + DELIMITER + normalizeBirthDate(birthDate);

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hashBytes = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("명의 해시 처리 실패", e);
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    private static String normalizeBirthDate(LocalDate birthDate) {
        return birthDate == null ? "" : birthDate.toString();
    }
}