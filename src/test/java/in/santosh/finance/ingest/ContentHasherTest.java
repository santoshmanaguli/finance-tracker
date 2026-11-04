package in.santosh.finance.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import in.santosh.finance.transaction.Source;

class ContentHasherTest {

    @Test
    void isStableHexSha256() {
        assertThat(ContentHasher.hash(Source.SMS, "AX-HDFCBK", "hello")).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void ignoresOperatorPrefixAndSurroundingWhitespace() {
        assertThat(ContentHasher.hash(Source.SMS, "AX-HDFCBK", "hello"))
                .isEqualTo(ContentHasher.hash(Source.SMS, "JD-HDFCBK-S", "  hello\n"));
    }

    @Test
    void differsBySourceAndSender() {
        String base = ContentHasher.hash(Source.SMS, "AX-HDFCBK", "hello");
        assertThat(ContentHasher.hash(Source.EMAIL, "AX-HDFCBK", "hello")).isNotEqualTo(base);
        assertThat(ContentHasher.hash(Source.SMS, "AX-SBIINB", "hello")).isNotEqualTo(base);
    }
}
