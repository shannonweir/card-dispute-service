package za.co.capitecbank.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

/** Application error definitions. Enum names are public response codes and must remain stable. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found"),
    CONFLICT(HttpStatus.CONFLICT, "Resource already exists"),
    TRANSACTION_INELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "Only posted transactions can be disputed"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "The request failed validation"),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "Choose a non-empty file"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Files must be no larger than 5 MB"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file must be a valid PDF, PNG or JPEG with a matching content type"),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "Invalid file name"),
    DOCUMENT_READ_FAILED(HttpStatus.BAD_REQUEST, "Unable to read supporting document"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required or token invalid"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Customer permission required"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultDetail;

    public String getCode() {
        return name();
    }
}
