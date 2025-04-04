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
    private final TokenTextSplitter tokenSplitter = new TokenTextSplitter();
    private final List<DocumentInfo> documentList = new ArrayList<>();

    public final static String DOCUMENT_ID = "documentId";

    public DocumentService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<DocumentInfo> getDocuments() {
        return documentList;
    }

    public DocumentInfo storeFile(MultipartFile file, String fileId) {
        writeToVectorStore(file, fileId);

        String fileName = Optional.ofNullable(file.getOriginalFilename())
                .orElse("Unknown");
        DocumentInfo documentInfo = new DocumentInfo(fileId, fileName, file.getSize(), Instant.now().toString());

        deleteDocuments();
        documentList.add(documentInfo);
        return documentInfo;
    }

    private void writeToVectorStore(MultipartFile file, String fileId) {
        Resource resource = file.getResource();
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, PdfDocumentReaderConfig.defaultConfig());

        List<Document> documents = tokenSplitter.split(pdfReader.read());
        for (Document document : documents) {
            document.getMetadata().put(DOCUMENT_ID, fileId);
        }
        vectorStore.write(documents);
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
