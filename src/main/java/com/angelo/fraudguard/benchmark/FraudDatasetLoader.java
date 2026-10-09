package com.angelo.fraudguard.benchmark;

import com.angelo.fraudguard.domain.Transaction;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads the vendored snapshot of the Sparkov "Credit Card Transactions Fraud
 * Detection" benchmark into FraudGuard {@link Transaction}s plus their fraud
 * labels.
 *
 * <p>The snapshot is a trimmed CSV with the header
 * {@code unix_time,cc_num,amt,category,state,is_fraud}. Each row becomes a
 * {@link Transaction} whose amount is carried in integer cents (so money is never
 * a {@code double}, matching the domain model), whose card id is the card number,
 * whose event time is the Unix timestamp, and whose country is {@code "US"} — the
 * dataset is US-domestic, which is documented in the data README. Rows are
 * returned sorted by event time so that FraudGuard's per-card velocity state is
 * built in the order the transactions actually occurred.
 *
 * <p>See {@code src/test/resources/data/README.md} for the source, licence and
 * the exact command that regenerates the snapshot.
 */
public final class FraudDatasetLoader {

    /** Classpath location of the vendored snapshot (gzip-compressed CSV). */
    public static final String DEFAULT_RESOURCE = "/data/creditcard_fraud_sample.csv.gz";

    private static final String EXPECTED_HEADER = "unix_time,cc_num,amt,category,state,is_fraud";

    private FraudDatasetLoader() {
    }

    /** Load the default vendored snapshot from the classpath. */
    public static List<FraudSample> loadDefault() {
        try (InputStream in = FraudDatasetLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("vendored dataset not found on classpath: " + DEFAULT_RESOURCE);
            }
            InputStream data = DEFAULT_RESOURCE.endsWith(".gz") ? new GZIPInputStream(in) : in;
            return load(data);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read vendored dataset " + DEFAULT_RESOURCE, e);
        }
    }

    /** Load and parse samples from an open CSV stream, sorted by ascending event time. */
    public static List<FraudSample> load(InputStream in) throws IOException {
        List<FraudSample> samples = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalStateException("empty dataset: no header row");
            }
            if (!header.strip().equals(EXPECTED_HEADER)) {
                throw new IllegalStateException(
                        "unexpected header; wanted [" + EXPECTED_HEADER + "] but got [" + header.strip() + "]");
            }
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                samples.add(parse(line, lineNo));
            }
        }
        if (samples.isEmpty()) {
            throw new IllegalStateException("dataset has a header but no data rows");
        }
        samples.sort(Comparator.comparing(s -> s.transaction().timestamp()));
        return List.copyOf(samples);
    }

    private static FraudSample parse(String line, int lineNo) {
        String[] f = line.split(",", -1);
        if (f.length != 6) {
            throw new IllegalStateException("line " + lineNo + " has " + f.length + " columns, expected 6: " + line);
        }
        long unixTime = Long.parseLong(f[0].trim());
        String cardId = f[1].trim();
        long cents = parseCents(f[2].trim());
        String category = f[3].trim();
        String state = f[4].trim();
        int fraudFlag = Integer.parseInt(f[5].trim());
        if (fraudFlag != 0 && fraudFlag != 1) {
            throw new IllegalStateException("line " + lineNo + " has non-binary is_fraud=" + fraudFlag);
        }
        String merchant = category.isEmpty() ? "unknown" : category;
        String id = cardId + "@" + unixTime + "#" + state;
        Transaction tx = new Transaction(
                id, cardId, cents, "USD", merchant, "US", Instant.ofEpochSecond(unixTime));
        return new FraudSample(tx, fraudFlag == 1);
    }

    /**
     * Parse a dollar amount like {@code 4.97} into integer cents without binary
     * floating point: split on the decimal point and combine the whole and
     * fractional parts so {@code 4.97 -> 497} and {@code 1000 -> 100000}.
     */
    static long parseCents(String amount) {
        int dot = amount.indexOf('.');
        if (dot < 0) {
            return Long.parseLong(amount) * 100L;
        }
        String whole = amount.substring(0, dot);
        String frac = amount.substring(dot + 1);
        if (frac.length() == 1) {
            frac = frac + "0";
        } else if (frac.length() > 2) {
            // Round to the nearest cent.
            int third = frac.charAt(2) - '0';
            long base = Long.parseLong(whole.isEmpty() ? "0" : whole) * 100L
                    + Long.parseLong(frac.substring(0, 2));
            return third >= 5 ? base + 1 : base;
        }
        long wholeCents = (whole.isEmpty() || whole.equals("-") ? 0L : Long.parseLong(whole)) * 100L;
        long fracCents = Long.parseLong(frac);
        return amount.startsWith("-") ? wholeCents - fracCents : wholeCents + fracCents;
    }
}
