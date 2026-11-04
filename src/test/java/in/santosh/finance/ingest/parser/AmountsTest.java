package in.santosh.finance.ingest.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountsTest {

    @ParameterizedTest(name = "\"{0}\" -> {1} paise")
    @CsvSource(delimiter = '|', value = {
            "500            | 50000",
            "500.5          | 50050",
            "500.50         | 50050",
            "0.01           | 1",
            "1,234.56       | 123456",
            "1,23,456.78    | 12345678",     // Indian lakh grouping
            "  2,000.00     | 200000",
            "1234.56        | 123456",       // the classic double bug: 1234.56 * 100 = 123455.99999
    })
    void convertsRupeeTextToExactPaise(String text, long expected) {
        assertThat(Amounts.toPaise(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "-50", "1.234", "1e3", "Rs.50", "1..2"})
    void rejectsAnythingThatIsNotAPlainAmount(String text) {
        assertThatThrownBy(() -> Amounts.toPaise(text)).isInstanceOf(NumberFormatException.class);
    }
}
