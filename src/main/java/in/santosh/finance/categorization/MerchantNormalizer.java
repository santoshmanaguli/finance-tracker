package in.santosh.finance.categorization;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns the many spellings banks use for one merchant into one comparable key.
 * <pre>
 *   "Swiggy Ltd"            -> "SWIGGY LTD"
 *   "SWIGGY*BANGALORE"      -> "SWIGGY BANGALORE"
 *   "swiggy.in@axisbank"    -> "SWIGGY IN"        (UPI VPA: handle before '@' only)
 *   "  Big  Basket  "       -> "BIG BASKET"
 * </pre>
 * Rules then match with CONTAINS "SWIGGY". Pure function, no Spring, no DB: trivially unit-testable.
 */
public final class MerchantNormalizer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]+");

    private MerchantNormalizer() {
    }

    public static String normalize(String merchantRaw) {
        if (merchantRaw == null || merchantRaw.isBlank()) {
            return null;
        }
        String s = merchantRaw.strip();
        int at = s.indexOf('@');
        if (at >= 0) {
            s = s.substring(0, at);     // UPI VPA: the bank handle (@okaxis, @ybl) is noise
        }
        // Locale.ROOT: toUpperCase() with a Turkish default locale turns "i" into "İ".
        s = NON_ALNUM.matcher(s.toUpperCase(Locale.ROOT)).replaceAll(" ").strip();
        return s.isEmpty() ? null : s;
    }
}
