package in.santosh.finance.ingest.parser;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indian commercial SMS senders (TRAI DLT) look like {@code AX-HDFCBK} or {@code VM-HDFCBK-S}:
 * <ul>
 *   <li>2-letter prefix = telecom operator + circle (varies by route, NOT stable)</li>
 *   <li>6-character header = the brand (stable: this is what identifies the bank)</li>
 *   <li>optional suffix -S/-T/-P/-G = service / transactional / promotional / government</li>
 * </ul>
 * The same bank can reach you as AX-HDFCBK today and JD-HDFCBK tomorrow, so we key parsers on
 * the header alone.
 */
public final class SenderIds {

    private static final Pattern DLT = Pattern.compile("^[A-Z]{2}-([A-Z0-9]{3,9})(?:-[A-Z])?$");

    private SenderIds() {
    }

    public static String normalize(String sender) {
        if (sender == null) {
            return "";
        }
        String s = sender.strip().toUpperCase(Locale.ROOT);
        Matcher m = DLT.matcher(s);
        return m.matches() ? m.group(1) : s;
    }
}
