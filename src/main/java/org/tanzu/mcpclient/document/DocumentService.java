package org.tanzu.mcpclient.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DocumentService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenSplitter = TokenTextSplitter.builder()
            .withChunkSize(512)
            .withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build();
    private final List<DocumentInfo> documentList = new ArrayList<>();

    public final static String DOCUMENT_ID = "documentId";

    public DocumentService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<DocumentInfo> getDocuments() {
        return new ArrayList<>(documentList); // Return defensive copy
    }

    public DocumentInfo storeFile(MultipartFile file, String fileId) {
        writeToVectorStore(file, fileId);

        String fileName = Optional.ofNullable(file.getOriginalFilename())
                .orElse("Unknown");
        DocumentInfo documentInfo = new DocumentInfo(fileId, fileName, file.getSize(), Instant.now().toString());

        // No longer delete all documents - just add the new one
        documentList.add(documentInfo);
        return documentInfo;
    }

    /**
     * Delete a specific document by its ID
     * @param documentId The ID of the document to delete
     * @return true if document was found and deleted, false if not found
     */
    public boolean deleteDocument(String documentId) {
        // Validate that document exists
        Optional<DocumentInfo> existingDocument = documentList.stream()
                .filter(doc -> doc.id().equals(documentId))
                .findFirst();

        if (existingDocument.isEmpty()) {
            return false; // Document not found
        }

        // Remove from vector store
        Filter.Expression filterExpression = new Filter.Expression(Filter.ExpressionType.EQ,
                new Filter.Key(DOCUMENT_ID),
                new Filter.Value(documentId)
        );
        vectorStore.delete(filterExpression);

        // Remove from document list
        documentList.removeIf(doc -> doc.id().equals(documentId));

        return true;
    }

    /**
     * Check if a document exists by its ID
     * @param documentId The ID of the document to check
     * @return true if document exists, false otherwise
     */
    public boolean documentExists(String documentId) {
        return documentList.stream()
                .anyMatch(doc -> doc.id().equals(documentId));
    }

    private void writeToVectorStore(MultipartFile file, String fileId) {
        List<Document> rawDocuments;

        try {
            Resource resource = file.getResource();
            var pdfReader = new PagePdfDocumentReader(resource, PdfDocumentReaderConfig.defaultConfig());
            rawDocuments = pdfReader.read();
        } catch (IllegalArgumentException | StackOverflowError e) {
            // ForkPDFLayoutTextStripper has known issues with certain PDFs:
            // - Broken Comparator: "Comparison method violates its general contract!" (IllegalArgumentException)
            // - Catastrophic regex backtracking in text position processing (StackOverflowError)
            // Fall back to plain PDFBox text extraction which avoids both issues.
            logger.warn("Layout-based PDF extraction failed for file {}, falling back to plain text extraction: {}",
                    file.getOriginalFilename(), e.getClass().getSimpleName() + ": " + e.getMessage());
            rawDocuments = readWithPlainTextExtractor(file);
        }

        try {
            List<Document> documents = tokenSplitter.split(rawDocuments);
            List<Document> sanitizedDocuments = new ArrayList<>();
            for (Document document : documents) {
                // Remove null characters that PostgreSQL can't handle
                String sanitizedText = document.getText().replace("\u0000", "");
                Document sanitizedDoc = new Document(sanitizedText, document.getMetadata());
                sanitizedDoc.getMetadata().put(DOCUMENT_ID, fileId);
                sanitizedDocuments.add(sanitizedDoc);
            }
            vectorStore.write(sanitizedDocuments);
        } catch (Exception e) {
            throw new RuntimeException("Unable to process PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback PDF reader using Apache PDFBox's plain PDFTextStripper.
     * Avoids the layout-aware ForkPDFLayoutTextStripper which has a broken comparator
     * that fails on PDFs with complex text positioning.
     */
    private List<Document> readWithPlainTextExtractor(MultipartFile file) {
        try (PDDocument pdDocument = Loader.loadPDF(file.getBytes())) {
            var stripper = new PDFTextStripper();
            int totalPages = pdDocument.getNumberOfPages();
            List<Document> documents = new ArrayList<>();

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(pdDocument);

                if (pageText != null && !pageText.isBlank()) {
                    documents.add(new Document(pageText, Map.of("page_number", page, "total_pages", totalPages)));
                }
            }

            logger.info("Plain text extraction completed for {}: {} pages yielded {} documents",
                    file.getOriginalFilename(), totalPages, documents.size());
            return documents;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read PDF file: " + e.getMessage(), e);
        }
    }

    public void deleteDocuments() {
        for (DocumentInfo documentInfo : documentList) {
            Filter.Expression filterExpression = new Filter.Expression(Filter.ExpressionType.EQ,
                    new Filter.Key(DOCUMENT_ID),
                    new Filter.Value(documentInfo.id)
            );

            vectorStore.delete(filterExpression);
        }

        documentList.clear();
    }

    public record DocumentInfo(String id, String name, long size, String uploadDate) {
    }
}