package org.tanzu.mcpclient.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {
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
        Resource resource = file.getResource();
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, PdfDocumentReaderConfig.defaultConfig());

        List<Document> documents = tokenSplitter.split(pdfReader.read());
        List<Document> sanitizedDocuments = new ArrayList<>();
        for (Document document : documents) {
            // Remove null characters that PostgreSQL can't handle
            String sanitizedText = document.getText().replace("\u0000", "");
            Document sanitizedDoc = new Document(sanitizedText, document.getMetadata());
            sanitizedDoc.getMetadata().put(DOCUMENT_ID, fileId);
            sanitizedDocuments.add(sanitizedDoc);
        }
        vectorStore.write(sanitizedDocuments);
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