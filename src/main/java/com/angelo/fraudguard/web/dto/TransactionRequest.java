package com.angelo.fraudguard.web.dto;

import com.angelo.fraudguard.domain.Transaction;
import com.angelo.fraudguard.exception.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The JSON body accepted at {@code POST /transactions}.
 *
 * <p>Fields are wrapper types so that a missing field is distinguishable from a
 * zero/empty one. Validation is explicit (see {@link #toTransaction()}) rather
 * than annotation-driven: the service deliberately depends only on
 * {@code spring-boot-starter-web}, so it stays small and builds offline without
 * the validation starter. The rules are simple enough to state in one place.
 *
 * @param id        unique transaction id (required)
 * @param cardId    card / account id (required)
 * @param amount    amount in integer cents, &gt;= 0 (required)
 * @param currency  ISO 4217 currency code, 3 letters (required)
 * @param merchant  merchant name (required)
 * @param country   ISO 3166-1 alpha-2 country code, 2 letters (required)
 * @param timestamp event time; defaults to now when omitted
 */
public record TransactionRequest(
        String id,
        String cardId,
        Long amount,
        String currency,
        String merchant,
        String country,
        Instant timestamp) {

    /**
     * Validate and convert to a domain {@link Transaction}.
     *
     * @throws ValidationException if any field is missing or malformed, with all
     *                             problems collected into one message
     */
    public Transaction toTransaction() {
        List<String> errors = new ArrayList<>();
        if (isBlank(id)) {
            errors.add("id is required");
        }
        if (isBlank(cardId)) {
            errors.add("cardId is required");
        }
        if (amount == null) {
            errors.add("amount is required");
        } else if (amount < 0) {
            errors.add("amount must be >= 0 (integer cents)");
        }
        if (isBlank(currency)) {
            errors.add("currency is required");
        } else if (currency.trim().length() != 3) {
            errors.add("currency must be a 3-letter ISO 4217 code");
        }
        if (isBlank(merchant)) {
            errors.add("merchant is required");
        }
        if (isBlank(country)) {
            errors.add("country is required");
        } else if (country.trim().length() != 2) {
            errors.add("country must be a 2-letter ISO 3166-1 code");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Transaction(
                id.trim(),
                cardId.trim(),
                amount,
                currency.trim(),
                merchant.trim(),
                country.trim(),
                timestamp != null ? timestamp : Instant.now());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
