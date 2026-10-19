package za.co.carddisputeportalservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.mapper.PortalMapper;
import za.co.capitecbank.model.Dispute;
import za.co.capitecbank.model.DisputeDocument;
import za.co.capitecbank.repository.DisputeDocumentRepository;
import za.co.capitecbank.service.DisputeService;
import za.co.capitecbank.service.DocumentService;
import za.co.carddisputeportalservice.generated.model.DisputeReason;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.imageio.ImageIO;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock DisputeService disputes;
    @Mock DisputeDocumentRepository documents;
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Test
    void storesValidUploadsWithSanitizedMetadata() throws Exception {
        UUID id = UUID.randomUUID();
        when(disputes.getOwnedDispute(id))
                .thenReturn(
                        new Dispute(
                                null, DisputeReason.OTHER, "A card transaction is incorrect", NOW));
        when(documents.save(any(DisputeDocument.class))).thenAnswer(call -> call.getArgument(0));
        byte[] bytes = "%PDF-1.7\nexample".getBytes();
        var result =
                service()
                        .uploadDisputeDocument(
                                id,
                                new MockMultipartFile(
                                        "file", "C:\\temp\\receipt.pdf", "application/pdf", bytes));
        assertThat(result.getFileName()).isEqualTo("receipt.pdf");
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getSizeBytes()).isEqualTo(bytes.length);
        assertThat(result.getUploadedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsEmptyUploadsWithoutPersistingThem() {
        assertThatThrownBy(
                        () ->
                                service()
                                        .uploadDisputeDocument(
                                                UUID.randomUUID(),
                                                new MockMultipartFile(
                                                        "file",
                                                        "empty.pdf",
                                                        "application/pdf",
                                                        new byte[0])))
                .isInstanceOf(CardDisputeException.class)
                .hasMessageContaining("non-empty");
        verifyNoInteractions(documents);
    }

    @Test
    void rejectsDeclaredMimeTypesThatDoNotMatchFileBytes() {
        assertThatThrownBy(
                        () ->
                                service()
                                        .uploadDisputeDocument(
                                                UUID.randomUUID(),
                                                new MockMultipartFile(
                                                        "file",
                                                        "fake.jpg",
                                                        "image/jpeg",
                                                        "%PDF-1.7\nexample".getBytes())))
                .isInstanceOf(CardDisputeException.class)
                .hasMessageContaining("matching content type");
        verifyNoInteractions(documents);
    }

    @ParameterizedTest
    @CsvSource({"png,image/png", "jpeg,image/jpeg"})
    void detectsAndStoresRealImages(final String format, final String mime) throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, buffer);
        UUID id = UUID.randomUUID();
        when(disputes.getOwnedDispute(id))
                .thenReturn(new Dispute(null, DisputeReason.OTHER, "Incorrect transaction", NOW));
        when(documents.save(any(DisputeDocument.class))).thenAnswer(call -> call.getArgument(0));

        var response =
                service()
                        .uploadDisputeDocument(
                                id,
                                new MockMultipartFile(
                                        "file", "receipt." + format, mime, buffer.toByteArray()));

        assertThat(response.getContentType()).isEqualTo(mime);
        assertThat(response.getSizeBytes()).isEqualTo(buffer.size());
    }

    @ParameterizedTest
    @CsvSource({"text/plain,plain text", "image/gif,GIF89a", "application/pdf,not a PDF"})
    void rejectsUnsupportedOrDisguisedContent(final String mime, final String content) {
        assertThatThrownBy(
                        () ->
                                service()
                                        .uploadDisputeDocument(
                                                UUID.randomUUID(),
                                                new MockMultipartFile(
                                                        "file",
                                                        "receipt.pdf",
                                                        mime,
                                                        content.getBytes())))
                .isInstanceOf(CardDisputeException.class)
                .hasMessageContaining("matching content type");
        verifyNoInteractions(documents);
    }

    @Test
    void rejectsUploadsOverTheSizeLimitBeforeReadingContent() {
        assertThatThrownBy(
                        () ->
                                service()
                                        .uploadDisputeDocument(
                                                UUID.randomUUID(),
                                                new MockMultipartFile(
                                                        "file",
                                                        "large.pdf",
                                                        "application/pdf",
                                                        new byte[5 * 1024 * 1024 + 1])))
                .isInstanceOf(CardDisputeException.class)
                .hasMessageContaining("5 MB");
        verifyNoInteractions(documents);
    }

    @Test
    void rejectsMissingMimeTypeAndEmptyFileName() {
        byte[] pdf = "%PDF-1.7\nexample".getBytes();
        assertThatThrownBy(
                        () ->
                                service()
                                        .uploadDisputeDocument(
                                                UUID.randomUUID(),
                                                new MockMultipartFile(
                                                        "file", "receipt.pdf", null, pdf)))
                .isInstanceOf(CardDisputeException.class)
                .hasMessageContaining("matching content type");
        assertThatThrownBy(
                        () ->
                                service()
                                        .uploadDisputeDocument(
                                                UUID.randomUUID(),
                                                new MockMultipartFile(
                                                        "file", "", "application/pdf", pdf)))
                .isInstanceOf(CardDisputeException.class)
                .hasMessageContaining("file name");
        verifyNoInteractions(documents);
    }

    private DocumentService service() {
        return new DocumentService(
                disputes,
                documents,
                Mappers.getMapper(PortalMapper.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
