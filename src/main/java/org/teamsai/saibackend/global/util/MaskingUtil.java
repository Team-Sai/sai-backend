package org.teamsai.saibackend.global.util;

public class MaskingUtil {

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 6) {
            return accountNumber;
        }

        int length = accountNumber.length();
        int maskLength = Math.min(3, length - 4);
        int start = (length - maskLength) / 2;

        StringBuilder sb = new StringBuilder(accountNumber);
        for (int i = start; i < start + maskLength; i++) {
            sb.setCharAt(i, '*');
        }
        return sb.toString();
    }
}
