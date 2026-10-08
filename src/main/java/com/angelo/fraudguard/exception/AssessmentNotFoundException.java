package com.angelo.fraudguard.exception;

/**
 * Thrown when no assessment exists for a transaction id — either the id is
 * unknown, or the transaction has been accepted but not yet scored (scoring is
 * asynchronous). Maps to {@code 404 Not Found}.
 */
public class AssessmentNotFoundException extends RuntimeException {

    public AssessmentNotFoundException(String transactionId) {
        super("no assessment found for transaction '" + transactionId
                + "' (unknown id, or not scored yet)");
    }
}
