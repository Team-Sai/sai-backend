package org.teamsai.saibackend.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

public final class LinkIdentityHasher {

    private static final String DELIMITER = "|";

    private LinkIdentityHasher() {
    }

    public static String hash(String name, LocalDate birthDate) {
        String normalized = normalizeName(name) + DELIMITER + normalizeBirthDate(birthDate);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("\\s+", "");
    }

    private static String normalizeBirthDate(LocalDate birthDate) {
        return birthDate == null ? "" : birthDate.toString();
    }
}