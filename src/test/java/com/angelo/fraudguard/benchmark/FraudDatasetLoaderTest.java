package com.angelo.fraudguard.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.angelo.fraudguard.domain.Transaction;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the loader reads the real vendored snapshot with the expected shape and
 * label distribution, and that the amount parsing is exact. These assertions fail
 * if the snapshot is swapped for something else or the parsing regresses, so the
 * benchmark cannot silently run on the wrong data.
 */
class FraudDatasetLoaderTest {

    @Test
    void loads_the_vendored_snapshot_with_the_expected_shape_and_label_distribution() {
        List<FraudSample> samples = FraudDatasetLoader.loadDefault();

        // Exact size and fraud count of the committed snapshot.
        assertThat(samples).hasSize(150_000);
        long fraud = samples.stream().filter(FraudSample::fraud).count();
        assertThat(fraud).isEqualTo(1_377L);

        // Heavily imbalanced, as real card fraud is: well under 2%.
        double baseRate = (double) fraud / samples.size();
        assertThat(baseRate).isBetween(0.005, 0.02);

        // Both classes are present (otherwise the ranking metrics are undefined).
        assertThat(samples.stream().anyMatch(FraudSample::fraud)).isTrue();
        assertThat(samples.stream().anyMatch(s -> !s.fraud())).isTrue();

        // Every transaction is well-formed for the scorer.
        assertThat(samples).allSatisfy(s -> {
            Transaction t = s.transaction();
            assertThat(t.cardId()).isNotBlank();
            assertThat(t.amount()).isGreaterThanOrEqualTo(0);
            assertThat(t.country()).isEqualTo("US");
            assertThat(t.currency()).isEqualTo("USD");
        });

        // Returned in ascending event-time order so velocity is built correctly.
        for (int i = 1; i < samples.size(); i++) {
            Instant prev = samples.get(i - 1).transaction().timestamp();
            Instant cur = samples.get(i).transaction().timestamp();
            assertThat(cur).isAfterOrEqualTo(prev);
        }

        // Fraud amounts skew much larger than legitimate ones in this dataset,
        // which is the signal FraudGuard's amount-velocity rule is built to catch.
        double meanFraud = samples.stream().filter(FraudSample::fraud)
                .mapToLong(s -> s.transaction().amount()).average().orElseThrow();
        double meanLegit = samples.stream().filter(s -> !s.fraud())
                .mapToLong(s -> s.transaction().amount()).average().orElseThrow();
        assertThat(meanFraud).isGreaterThan(meanLegit * 3);
    }

    @Test
    void parses_amounts_into_exact_cents_without_floating_point() {
        assertThat(FraudDatasetLoader.parseCents("4.97")).isEqualTo(497);
        assertThat(FraudDatasetLoader.parseCents("1000")).isEqualTo(100_000);
        assertThat(FraudDatasetLoader.parseCents("1000.00")).isEqualTo(100_000);
        assertThat(FraudDatasetLoader.parseCents("0.01")).isEqualTo(1);
        assertThat(FraudDatasetLoader.parseCents("12.5")).isEqualTo(1_250);
        assertThat(FraudDatasetLoader.parseCents("1.005")).isEqualTo(101); // rounds to nearest cent
        assertThat(FraudDatasetLoader.parseCents("1.004")).isEqualTo(100);
    }

    @Test
    void rejects_a_snapshot_with_the_wrong_header() {
        String bad = "time,card,amount\n1,abc,1.00\n";
        assertThatThrownBy(() -> FraudDatasetLoader.load(
                new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected header");
    }

    @Test
    void parses_a_minimal_well_formed_stream() throws Exception {
        String csv = "unix_time,cc_num,amt,category,state,is_fraud\n"
                + "1325376018,2703186189652095,4.97,misc_net,NC,0\n"
                + "1325376044,38859492057661,1000.00,shopping_net,WA,1\n";
        List<FraudSample> samples = FraudDatasetLoader.load(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(samples).hasSize(2);
        assertThat(samples.get(0).transaction().amount()).isEqualTo(497);
        assertThat(samples.get(0).fraud()).isFalse();
        assertThat(samples.get(1).transaction().amount()).isEqualTo(100_000);
        assertThat(samples.get(1).fraud()).isTrue();
        assertThat(samples.get(1).transaction().cardId()).isEqualTo("38859492057661");
    }
}
