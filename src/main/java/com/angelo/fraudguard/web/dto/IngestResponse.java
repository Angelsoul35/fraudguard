package com.angelo.fraudguard.web.dto;

/**
 * The {@code 202 Accepted} body returned by {@code POST /transactions}. The
 * transaction has been published to the bus for asynchronous scoring; poll
 * {@code assessmentUrl} for the result.
 *
 * @param transactionId the accepted transaction's id
 * @param status        always {@code "accepted"}
 * @param assessmentUrl where to fetch the assessment once scoring completes
 */
public record IngestResponse(String transactionId, String status, String assessmentUrl) {

    public static IngestResponse accepted(String transactionId) {
        return new IngestResponse(transactionId, "accepted",
                "/transactions/" + transactionId + "/assessment");
    }
}
