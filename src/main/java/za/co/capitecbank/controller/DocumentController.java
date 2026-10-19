package za.co.capitecbank.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.carddisputeportalservice.generated.api.DocumentsApi;
import za.co.carddisputeportalservice.generated.model.DisputeDocumentResponse;
import za.co.capitecbank.service.DocumentService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DocumentController implements DocumentsApi {

    private final DocumentService documentService;

    @Override
    public ResponseEntity<List<DisputeDocumentResponse>> getDisputeDocuments(final UUID disputeId) {
        return ResponseEntity.ok(documentService.getDisputeDocuments(disputeId));
    }

    @Override
    public ResponseEntity<DisputeDocumentResponse> uploadDisputeDocument(final UUID disputeId, final MultipartFile file) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(documentService.uploadDisputeDocument(disputeId, file));
        } catch (IOException exception) {
            throw new CardDisputeException(ErrorCode.DOCUMENT_READ_FAILED);
        }
    }
}
