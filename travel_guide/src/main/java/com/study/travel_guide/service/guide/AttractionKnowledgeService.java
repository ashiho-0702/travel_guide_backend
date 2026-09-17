package com.study.travel_guide.service.guide;

import com.study.travel_guide.service.rag.EmbeddingService;
import com.study.travel_guide.service.rag.RetrievedDoc;
import com.study.travel_guide.service.rag.VectorStoreService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AttractionKnowledgeService {

    private static final String COLLECTION = "attraction_guide";

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;

    public AttractionKnowledgeService(EmbeddingService embeddingService, VectorStoreService vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    public void ingest(String text, String attraction) {
        List<String> chunks = chunk(text, 500);
        if (chunks.isEmpty()) {
            return;
        }
        List<float[]> vectors = embeddingService.embedBatch(chunks);
        vectorStore.ensureCollectionIfNeeded(COLLECTION, vectors.get(0).length);
        vectorStore.upsert(COLLECTION, vectors, chunks, "attraction", attraction);
    }

    public List<RetrievedDoc> search(String query, String attraction, int topK) {
        float[] qv = embeddingService.embed(query);
        vectorStore.ensureCollectionIfNeeded(COLLECTION, qv.length);
        return vectorStore.search(COLLECTION, qv, topK, "attraction", attraction);
    }

    private List<String> chunk(String text, int maxChars) {
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
