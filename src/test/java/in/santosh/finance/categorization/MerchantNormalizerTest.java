package in.santosh.finance.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MerchantNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Swiggy Ltd          | SWIGGY LTD",
            "SWIGGY*BANGALORE    | SWIGGY BANGALORE",
            "swiggy.in@axisbank  | SWIGGY IN",
            "'  Big   Basket  '  | BIG BASKET",   // quoted: CsvSource trims unquoted values
            "paytm-123456@paytm  | PAYTM 123456",
    })
    void normalizes(String raw, String expected) {
        assertThat(MerchantNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "***", "@ybl"})
    void returnsNullWhenNothingMeaningfulRemains(String raw) {
        assertThat(MerchantNormalizer.normalize(raw)).isNull();
    }
}
