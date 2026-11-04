package in.santosh.finance.ingest.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SenderIdsTest {

    @ParameterizedTest
    @CsvSource({
            "AX-HDFCBK,   HDFCBK",
            "JD-HDFCBK,   HDFCBK",      // different operator, same bank
            "VM-HDFCBK-S, HDFCBK",      // TRAI service suffix
            "ad-sbiinb-t, SBIINB",      // case-insensitive
            "HDFCBK,      HDFCBK",      // already bare
            "+919876543210, +919876543210",  // phone numbers pass through unchanged
    })
    void stripsOperatorPrefixAndCategorySuffix(String sender, String expected) {
        assertThat(SenderIds.normalize(sender)).isEqualTo(expected);
    }
}
