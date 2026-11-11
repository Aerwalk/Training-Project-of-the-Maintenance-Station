package org.example.sto.ui.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Тесты форматирования значений интерфейса: суммы, даты, склонения.
 */
class FmtTest {

    @Test
    void formatsMoneyWithRubleSign() {
        assertEquals("1235₽", noSpaces(Fmt.money(new BigDecimal("1234.6"))));
        assertEquals("1234₽", noSpaces(Fmt.money(new BigDecimal("1234.4"))));
    }

    @Test
    void formatsZeroAndNullMoney() {
        assertEquals("0₽", noSpaces(Fmt.money((BigDecimal) null)));
        assertEquals("0₽", noSpaces(Fmt.money(new BigDecimal("0"))));
    }

    @Test
    void formatsMoneyWithDecimals() {
        assertEquals("12,50₽", noSpaces(Fmt.money(new BigDecimal("12.5"), true)));
    }

    /** Убирает любые пробельные разделители разрядов, включая неразрывные. */
    private static String noSpaces(String value) {
        return value.replace("\u00A0", "").replace("\u202F", "").replace(" ", "");
    }

    @Test
    void parsesMoneyWithSpacesCommaAndSign() {
        assertEquals(new BigDecimal("1234.50"), Fmt.parseMoney(" 1 234,50 ₽ "));
        assertEquals(new BigDecimal("900"), Fmt.parseMoney("900"));
        assertNull(Fmt.parseMoney("  "));
        assertNull(Fmt.parseMoney(null));
    }

    @Test
    void parsesIntegers() {
        assertEquals(2024, Fmt.parseInteger(" 2024 "));
        assertNull(Fmt.parseInteger(""));
    }

    @Test
    void declinesRussianWords() {
        assertEquals("заявка", Fmt.pluralWord(1, "заявка", "заявки", "заявок"));
        assertEquals("заявки", Fmt.pluralWord(2, "заявка", "заявки", "заявок"));
        assertEquals("заявок", Fmt.pluralWord(5, "заявка", "заявки", "заявок"));
        assertEquals("заявок", Fmt.pluralWord(11, "заявка", "заявки", "заявок"));
        assertEquals("заявка", Fmt.pluralWord(21, "заявка", "заявки", "заявок"));
        assertEquals("заявки", Fmt.pluralWord(22, "заявка", "заявки", "заявок"));
        assertEquals("заявок", Fmt.pluralWord(0, "заявка", "заявки", "заявок"));
    }

    @Test
    void buildsInitialsAndShortName() {
        assertEquals("ИП", Fmt.initials("Иванов Пётр"));
        assertEquals("?", Fmt.initials(" "));
        assertEquals("коллега", Fmt.shortName(null));
        assertEquals("Пётр", Fmt.shortName("Иванов Пётр Сергеевич"));
    }

    @Test
    void formatsDatesAndHandlesNull() {
        LocalDateTime moment = LocalDateTime.of(2026, 11, 12, 9, 5);
        assertEquals("12.11.2026 09:05", Fmt.dateTime(moment));
        assertEquals("09:05", Fmt.time(moment));
        assertEquals("—", Fmt.dateTime(null));
        assertEquals("—", Fmt.time(null));
    }

    @Test
    void mapsStatusesAndRolesToRussianLabels() {
        assertEquals("new", Fmt.statusClass("новая"));
        assertEquals("progress", Fmt.statusClass("в работе"));
        assertEquals("done", Fmt.statusClass("завершена"));
        assertEquals("cancel", Fmt.statusClass("отменена"));
        assertEquals("neutral", Fmt.statusClass(null));

        assertEquals("Администратор", Fmt.role("admin"));
        assertEquals("Мастер", Fmt.role("master"));
        assertEquals("Пользователь", Fmt.role("неизвестно"));
    }
}
