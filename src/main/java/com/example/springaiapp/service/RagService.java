package com.example.springaiapp.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RagService {
    private static final Logger logger = LoggerFactory.getLogger(RagService.class);

    private final ChatClient chatClient;

    @Autowired
    VectorStore vectorStore;
    
    public RagService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String processQuery(String query) {
        String answer = "";
        List<Document> similarContexts = vectorStore.similaritySearch(SearchRequest.builder().query(query).similarityThreshold(0.8).topK(3).build());
        String context = similarContexts.stream()
                .map(ch -> String.format("Q: %s\nA: %s", ch.getMetadata().get("prompt"), ch.getText()))
                .collect(Collectors.joining("\n\n"));
        logger.debug("Found {} similar contexts", similarContexts.size());
        String promptText = String.format("""
            Use these previous Q&A pairs as context for answering the new question:

            Previous interactions:
            %s
            
            New question: %s
            
            Please provide a clear and educational response.""",
            context,
            query
        );
        answer = this.chatClient.prompt()
            .user(promptText)
            .call()
            .content();
        return answer;
    }
}
