package com.baytna.domain

import com.baytna.domain.money.Money
import com.baytna.domain.money.MoneyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {
    @Test fun parsesWholePounds() = assertEquals(Money(125_000), MoneyParser.parse("1250"))
    @Test fun parsesPiastres() = assertEquals(Money(1250), MoneyParser.parse("12.5"))
    @Test fun parsesTwoDecimals() = assertEquals(Money(1205), MoneyParser.parse("12.05"))
    @Test fun parsesArabicIndicDigits() = assertEquals(Money(1250), MoneyParser.parse("١٢٫٥٠"))
    @Test fun parsesLeadingDot() = assertEquals(Money(50), MoneyParser.parse(".5"))
    @Test fun ignoresGroupingSpaces() = assertEquals(Money(100_000_00), MoneyParser.parse("100 000"))
    @Test fun rejectsThreeDecimals() = assertNull(MoneyParser.parse("1.234"))
    @Test fun rejectsLetters() = assertNull(MoneyParser.parse("12a"))
    @Test fun rejectsEmpty() = assertNull(MoneyParser.parse(""))
    @Test fun rejectsLoneDot() = assertNull(MoneyParser.parse("."))
    @Test fun rejectsNegative() = assertNull(MoneyParser.parse("-5"))
    @Test fun rejectsTwoSeparators() = assertNull(MoneyParser.parse("1.2.3"))
    @Test fun commaAsDecimal() = assertEquals(Money(1250), MoneyParser.parse("12,5"))
    @Test fun commaThousands() = assertEquals(Money(125_000), MoneyParser.parse("1,250"))
    @Test fun commaThousandsWithDecimals() = assertEquals(Money(125_050), MoneyParser.parse("1,250.50"))
    @Test fun manyCommaGroups() = assertEquals(Money(1_250_000_00), MoneyParser.parse("1,250,000"))
    @Test fun rejectsBadCommaGroups() = assertNull(MoneyParser.parse("12,50.5"))
    @Test fun noFloatingPointDrift() = assertEquals(Money(30), MoneyParser.parse("0.30"))

    @Test fun editableRoundTrip() {
        assertEquals("12.50", MoneyParser.toEditable(Money(1250)))
        assertEquals("12", MoneyParser.toEditable(Money(1200)))
        assertEquals("0.05", MoneyParser.toEditable(Money(5)))
        assertEquals("-3.10", MoneyParser.toEditable(Money(-310)))
    }
}
