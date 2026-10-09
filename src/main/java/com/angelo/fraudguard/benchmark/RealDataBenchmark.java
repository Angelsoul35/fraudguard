package com.angelo.fraudguard.benchmark;

import com.angelo.fraudguard.config.ScoringProperties;
import java.util.List;

/**
 * Command-line entry point for the real-data benchmark.
 *
 * <p>It loads the vendored Sparkov snapshot, runs FraudGuard's production scorer
 * through {@link BenchmarkEvaluator}, and prints the headline block. The same
 * evaluation is asserted by {@code RealDataBenchmarkTest}; this class exists so
 * the benchmark can also be run on demand, for example:
 *
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.angelo.fraudguard.benchmark.RealDataBenchmark
 * </pre>
 *
 * (or from a packaged jar). The scoring configuration is
 * {@link #benchmarkConfig()}, tuned to the dataset's multi-week cadence and
 * documented in the README; it is a configuration choice, not a code change.
 */
public final class RealDataBenchmark {

    private RealDataBenchmark() {
    }

    /**
     * The scoring configuration used for the benchmark. The defaults ship tuned for
     * a 10-minute real-time stream; this dataset spans weeks per card, so the
     * velocity window and the burst/spike thresholds are set to the data's cadence.
     * Weights, the review/decline thresholds and the rule logic are unchanged.
     */
    public static ScoringProperties benchmarkConfig() {
        return new ScoringProperties(
                java.time.Duration.ofDays(30),                        // velocity window: ~1 month per-card baseline
                40,                                                   // review threshold (default)
                70,                                                   // decline threshold (default)
                new ScoringProperties.Velocity(3.5, 3, 30),          // amount spike: >=3.5x a >=3-tx windowed average
                new ScoringProperties.Frequency(160, 25),            // burst: >=160 prior in 30d (far above the ~64/mo norm)
                new ScoringProperties.Travel(900.0, 40),             // unchanged (US-domestic data; rule inert here)
                new ScoringProperties.RoundAmount(100_000, 15),      // unchanged ($1000 round-value flag)
                new ScoringProperties.NewCountry(20, 30));           // unchanged (US-domestic data; rule inert here)
    }

    public static void main(String[] args) {
        List<FraudSample> samples = FraudDatasetLoader.loadDefault();
        EvaluationReport report = BenchmarkEvaluator.defaults().evaluate(samples, benchmarkConfig());
        System.out.println(report.format());
    }
}
