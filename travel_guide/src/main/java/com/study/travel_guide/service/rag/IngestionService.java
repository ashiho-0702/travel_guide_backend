package com.study.travel_guide.service.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;

    public IngestionService(EmbeddingService embeddingService, VectorStoreService vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    public void ingest(String text, String city) {
        ingestChunks(chunk(text, 500), city);
    }

    public void ingestChunks(List<String> chunks, String city) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<float[]> vectors = embeddingService.embedBatch(chunks);
        vectorStore.ensureCollectionIfNeeded(vectors.get(0).length);
        vectorStore.upsert(vectors, chunks, city);
    }

    public List<String> chunk(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\n")) {
            String para = line.trim();
            if (para.isEmpty()) {
                continue;
            }
            if (current.length() > 0 && current.length() + para.length() + 1 > maxChars) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(para);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
