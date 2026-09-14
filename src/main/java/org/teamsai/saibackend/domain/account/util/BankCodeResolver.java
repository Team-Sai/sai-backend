package org.teamsai.saibackend.domain.account.util;

public class BankCodeResolver {

    private BankCodeResolver() {}

    public static String resolveBankName(String bankCode) {
        return switch (bankCode) {
            case "004" -> "국민은행";
            case "020" -> "우리은행";
            case "088" -> "신한은행";
            default -> "기타은행";
        };
    }
}
