package za.co.capitecbank.exception;

import lombok.Getter;

import java.util.Objects;

/** An expected application failure with a safe, client-facing detail. */
@Getter
public class CardDisputeException extends RuntimeException {

    private final ErrorCode errorCode;

    public CardDisputeException(final ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultDetail());
    }

    public CardDisputeException(final ErrorCode errorCode, final String detail) {
        super(detail);
        this.errorCode = Objects.requireNonNull(errorCode);
    }
}
