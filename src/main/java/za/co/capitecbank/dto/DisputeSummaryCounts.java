package za.co.capitecbank.dto;

/** One database snapshot of transaction and dispute counts for a customer/account scope. */
public interface DisputeSummaryCounts {

    long getTotalTransactions();

    long getDisputesSubmitted();

    long getInProgress();

    long getResolved();

    long getRejected();
}
