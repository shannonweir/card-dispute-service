package za.co.capitecbank.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import za.co.carddisputeportalservice.generated.api.DisputeApi;
import za.co.carddisputeportalservice.generated.model.CreateDisputeRequest;
import za.co.carddisputeportalservice.generated.model.DisputePage;
import za.co.carddisputeportalservice.generated.model.DisputeReasonOption;
import za.co.carddisputeportalservice.generated.model.DisputeResponse;
import za.co.carddisputeportalservice.generated.model.DisputeStatus;
import za.co.carddisputeportalservice.generated.model.DisputeSummary;
import za.co.carddisputeportalservice.generated.model.TimelineEventResponse;
import za.co.capitecbank.service.DisputeService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DisputeController implements DisputeApi {

    private final DisputeService disputeService;

    @Override
    public ResponseEntity<DisputePage> getDisputes(final UUID accountId, final Integer page, final Integer size, final DisputeStatus status) {
        return ResponseEntity.ok(disputeService.getDisputes(accountId, page, size, status));
    }

    @Override
    public ResponseEntity<List<DisputeReasonOption>> getDisputeReasons() {
        return ResponseEntity.ok(disputeService.getDisputeReasons());
    }

    @Override
    public ResponseEntity<DisputeSummary> getDisputeSummary(final UUID accountId) {
        return ResponseEntity.ok(disputeService.getDisputeSummary(accountId));
    }

    @Override
    public ResponseEntity<DisputeResponse> getDisputeById(final UUID disputeId) {
        return ResponseEntity.ok(disputeService.getDisputeById(disputeId));
    }

    @Override
    public ResponseEntity<List<TimelineEventResponse>> getDisputeTimeline(final UUID disputeId) {
        return ResponseEntity.ok(disputeService.getDisputeTimeline(disputeId));
    }

    @Override
    public ResponseEntity<DisputeResponse> createDispute(final String idempotencyKey, final CreateDisputeRequest request) {
        var created = disputeService.createDispute(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/v1/disputes/" + created.getId()))
                .body(created);
    }
}
