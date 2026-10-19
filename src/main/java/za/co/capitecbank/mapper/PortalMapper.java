package za.co.capitecbank.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import za.co.capitecbank.dto.DisputeSummaryCounts;
import za.co.capitecbank.model.Account;
import za.co.capitecbank.model.Customer;
import za.co.capitecbank.model.Dispute;
import za.co.capitecbank.model.DisputeDocument;
import za.co.capitecbank.model.DisputeEvent;
import za.co.capitecbank.model.TransactionRecord;
import za.co.carddisputeportalservice.generated.model.AccountSummary;
import za.co.carddisputeportalservice.generated.model.CustomerProfile;
import za.co.carddisputeportalservice.generated.model.DisputeDocumentResponse;
import za.co.carddisputeportalservice.generated.model.DisputePage;
import za.co.carddisputeportalservice.generated.model.DisputeResponse;
import za.co.carddisputeportalservice.generated.model.DisputeSummary;
import za.co.carddisputeportalservice.generated.model.Money;
import za.co.carddisputeportalservice.generated.model.TimelineEventResponse;
import za.co.carddisputeportalservice.generated.model.TransactionPage;
import za.co.carddisputeportalservice.generated.model.TransactionResponse;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PortalMapper {

    @Mapping(target = "accounts", source = "accounts")
    CustomerProfile map(final Customer customer, final List<Account> accounts);

    AccountSummary map(final Account account);

    DisputeSummary map(final DisputeSummaryCounts counts);

    @Mapping(target = "transactionId", source = "transaction.id")
    @Mapping(target = "details", source = "description")
    @Mapping(target = "accountId", source = "transaction.account.id")
    @Mapping(target = "customerId", source = "transaction.account.customer.id")
    DisputeResponse map(final Dispute dispute);

    @Mapping(target = "description", source = "detail")
    TimelineEventResponse map(final DisputeEvent event);

    @Mapping(target = "merchant", source = "transaction.merchantName")
    @Mapping(target = "occurredAt", source = "transaction.transactionTime")
    @Mapping(target = "amount", source = "transaction")
    @Mapping(target = "accountId", source = "transaction.account.id")
    @Mapping(target = "customerId", source = "transaction.account.customer.id")
    TransactionResponse map(
            final TransactionRecord transaction,
            final boolean disputeEligible,
            final String disputeIneligibilityReason,
            final UUID disputeId);

    Money mapMoney(final TransactionRecord transaction);

    DisputeDocumentResponse map(final DisputeDocument document);

    default TransactionPage transactionPage(final Page<TransactionResponse> source) {
        return new TransactionPage()
                .content(source.getContent())
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages());
    }

    default DisputePage disputePage(final Page<DisputeResponse> source) {
        return new DisputePage()
                .content(source.getContent())
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages());
    }
}
