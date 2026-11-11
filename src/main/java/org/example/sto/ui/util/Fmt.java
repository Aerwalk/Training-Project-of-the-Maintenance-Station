package org.example.sto.ui.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Единые правила форматирования значений в интерфейсе.
 */
public final class Fmt {

    private static final Locale RU = Locale.of("ru", "RU");
    private static final DateTimeFormatter SHORT_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", RU);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", RU);
    private static final DateTimeFormatter TIME_ONLY =
            DateTimeFormatter.ofPattern("HH:mm", RU);
    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(RU);
        symbols.setGroupingSeparator('\u00A0');
        MONEY = new DecimalFormat("#,##0", symbols);
        MONEY.setRoundingMode(RoundingMode.HALF_UP);
    }

    private Fmt() {
    }

    public static String money(BigDecimal value) {
        return money(value, false);
    }

    public static String money(double value) {
        return money(BigDecimal.valueOf(value));
    }

    public static String money(BigDecimal value, boolean withDecimals) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        String pattern = withDecimals ? "#,##0.00" : "#,##0";
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(RU));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value) + " ₽";
    }

    public static String integer(long value) {
        return MONEY.format(value);
    }

    public static String shortDate(LocalDateTime value) {
        return value == null ? "—" : SHORT_DATE.format(value);
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "—" : DATE_TIME.format(value);
    }

    public static String time(LocalDateTime value) {
        return value == null ? "—" : TIME_ONLY.format(value);
    }

    public static String pluralWord(long count, String one, String few, String many) {
        long abs = Math.abs(count) % 100;
        long last = abs % 10;
        if (abs > 10 && abs < 20) {
            return many;
        }
        if (last == 1) {
            return one;
        }
        return last >= 2 && last <= 4 ? few : many;
    }

    public static String plural(long count, String one, String few, String many) {
        return count + " " + pluralWord(count, one, few, many);
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) {
                result.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return result.isEmpty() ? "?" : result.toString();
    }

    /** Короткое обращение по имени: «Иван Петров» → «Петров». */
    public static String shortName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "коллега";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts.length > 1 ? parts[1] : parts[0];
    }

    public static String role(String role) {
        if (role == null) {
            return "Пользователь";
        }
        return switch (role) {
            case "admin" -> "Администратор";
            case "manager" -> "Менеджер";
            case "master" -> "Мастер";
            case "client" -> "Клиент";
            default -> "Пользователь";
        };
    }

    public static String nullSafe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    public static String statusClass(String status) {
        if (status == null) {
            return "neutral";
        }
        return switch (status) {
            case "новая" -> "new";
            case "в работе" -> "progress";
            case "завершена" -> "done";
            case "отменена" -> "cancel";
            default -> "neutral";
        };
    }

    public static BigDecimal parseMoney(String value) {
        String normalized = value == null ? "" : value.replace('\u00A0', ' ')
                .replace(" ", "")
                .replace(',', '.')
                .replace("₽", "")
                .trim();
        return normalized.isEmpty() ? null : new BigDecimal(normalized);
    }

    public static Integer parseInteger(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : Integer.valueOf(normalized);
    }
}
