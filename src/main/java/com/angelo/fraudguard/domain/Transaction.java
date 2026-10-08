package com.angelo.fraudguard.domain;

import java.time.Instant;

/**
 * An immutable payment-card transaction submitted for scoring.
 *
 * <p>The monetary amount is carried as an integer number of {@code cents} to
 * avoid floating-point rounding: money is never a {@code double}. The
 * {@code country} is an ISO 3166-1 alpha-2 code (e.g. {@code "US"}, {@code "ES"}),
 * and {@code timestamp} is the <em>event time</em> of the transaction, which the
 * velocity features use rather than the time the event happens to be processed.
 *
 * @param id        unique transaction id supplied by the caller
 * @param cardId    the card / account the transaction belongs to (the velocity key)
 * @param amount    the amount in integer cents (always &gt;= 0)
 * @param currency  ISO 4217 currency code, e.g. {@code "USD"}
 * @param merchant  free-text merchant name
 * @param country   ISO 3166-1 alpha-2 country code where the transaction occurred
 * @param timestamp the transaction's event time
 */
public record Transaction(
        String id,
        String cardId,
        long amount,
        String currency,
        String merchant,
        String country,
        Instant timestamp) {

    public Transaction {
        if (country != null) {
            country = country.toUpperCase();
        }
        if (currency != null) {
            currency = currency.toUpperCase();
        }
    }

    /** The amount expressed in whole currency units (for display only). */
    public double majorUnits() {
        return amount / 100.0;
    }
}
