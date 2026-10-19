package za.co.capitecbank.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import za.co.carddisputeportalservice.generated.api.TransactionApi;
import za.co.carddisputeportalservice.generated.model.TransactionPage;
import za.co.carddisputeportalservice.generated.model.TransactionResponse;
import za.co.carddisputeportalservice.generated.model.TransactionType;
import za.co.capitecbank.service.TransactionService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TransactionController implements TransactionApi {

    private final TransactionService transactionService;

    @Override
    public ResponseEntity<TransactionPage> getTransactions(final UUID accountId, final Integer page,
                                                           final Integer size, final LocalDate from, final LocalDate to,
                                                           final String search, final TransactionType type) {
        return ResponseEntity.ok(transactionService.getTransactions(accountId, page, size, from, to, search, type));
    }

    @Override
    public ResponseEntity<TransactionResponse> getTransactionById(final UUID transactionId) {
        return ResponseEntity.ok(transactionService.getTransactionById(transactionId));
    }
}
