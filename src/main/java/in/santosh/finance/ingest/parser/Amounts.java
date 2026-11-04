package in.santosh.finance.ingest.parser;

import java.math.BigDecimal;

/**
 * Converts the amount text banks write into paise.
 *
 * Uses BigDecimal, never double: {@code Double.parseDouble("1234.56") * 100} is
 * 123455.99999999999, which truncates to the wrong number of paise.
 */
public final class Amounts {

    private Amounts() {
    }

    /**
     * Accepts Indian digit grouping and optional decimals: "500", "1,234.5", "1,23,456.78".
     * Currency prefixes are the parser regex's job, not this method's.
     *
     * @throws NumberFormatException if not a valid non-negative amount with at most 2 decimals
     */
    public static long toPaise(String amountText) {
        if (amountText == null || amountText.isBlank()) {
            throw new NumberFormatException("empty amount");
        }
        String digits = amountText.strip().replace(",", "");
        if (!digits.matches("\\d+(\\.\\d{1,2})?")) {
            throw new NumberFormatException("not an amount: " + amountText);
        }
        // movePointRight(2): rupees -> paise exactly. longValueExact throws instead of
        // silently truncating if something unexpected slips through.
        return new BigDecimal(digits).movePointRight(2).longValueExact();
    }
}
