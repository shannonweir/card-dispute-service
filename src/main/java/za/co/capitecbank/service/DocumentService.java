package za.co.capitecbank.service;

import lombok.RequiredArgsConstructor;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.mapper.PortalMapper;
import za.co.capitecbank.model.DisputeDocument;
import za.co.capitecbank.repository.DisputeDocumentRepository;
import za.co.carddisputeportalservice.generated.model.DisputeDocumentResponse;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final Tika tika = new Tika();

    private static final Set<String> ALLOWED_TYPES =
            Set.of("application/pdf", "image/png", "image/jpeg");

    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private final DisputeService disputeService;
    private final DisputeDocumentRepository disputeDocumentRepository;
    private final PortalMapper portalMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<DisputeDocumentResponse> getDisputeDocuments(final UUID disputeId) {
        disputeService.requireOwnedDispute(disputeId);
        return disputeDocumentRepository.findByDisputeIdOrderByUploadedAtAsc(disputeId).stream()
                .map(portalMapper::map)
                .toList();
    }

    @Transactional
    public DisputeDocumentResponse uploadDisputeDocument(final UUID disputeId, final MultipartFile file) throws IOException {

        var dispute = disputeService.getOwnedDispute(disputeId);
        if (file.isEmpty()) throw new CardDisputeException(ErrorCode.EMPTY_FILE);
        if (file.getSize() > MAX_BYTES) throw new CardDisputeException(ErrorCode.PAYLOAD_TOO_LARGE);
        byte[] bytes = file.getBytes();

        String detected = tika.detect(bytes);

        if (!ALLOWED_TYPES.contains(detected) || !detected.equals(file.getContentType())) {
            throw new CardDisputeException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        String name = Optional.ofNullable(file.getOriginalFilename())
                        .orElse("document")
                        .replace('\\', '/');

        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty() || name.length() > 255)
            throw new CardDisputeException(ErrorCode.INVALID_FILE_NAME);

        return portalMapper.map(disputeDocumentRepository.save(new DisputeDocument(dispute, name, detected, bytes, clock.instant())));
    }
}
