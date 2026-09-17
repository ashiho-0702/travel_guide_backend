package com.study.travel_guide.service.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStore;

    @Value("${vectorstore.collection}")
    private String collection;

    public RetrievalService(EmbeddingService embeddingService, VectorStoreService vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    public List<RetrievedDoc> retrieve(String query, int topK, String city) {
        float[] queryVector = embeddingService.embed(query);
        vectorStore.ensureCollectionIfNeeded(collection, queryVector.length);
        return vectorStore.search(collection, queryVector, topK, "city", city);
    }
}
