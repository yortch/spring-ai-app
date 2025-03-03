package com.example.springaiapp.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class DocumentService {
 
    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    VectorStore vectorStore;

    @PostConstruct
    private void init() {
        vectorStore.add(documents);
        logger.info("DocumentService initialized with. Document count: {}", 
                   documents.size());
    }

    List<Document> documents = List.of(
        new Document("3e1a1af7-c872-4e36-9faa-fe53b9613c69",
                    """
                    The Spring AI project aims to streamline the development of applications that 
                    incorporate artificial intelligence functionality without unnecessary complexity. 
                    The project draws inspiration from notable Python projects, such as LangChain 
                    and LlamaIndex, but Spring AI is not a direct port of those projects. 
                    The project was founded with the belief that the next wave of Generative AI 
                    applications will not be only for Python developers but will be ubiquitous 
                    across many programming languages.
                    """,
                     Map.of("prompt", "What is Spring AI?")),
        new Document("7a7c2caf-ce9c-4dcb-a543-937b76ef1098", 
                    """
                    A vector database stores data that the AI model is unaware of. When a user 
                    question is sent to the AI model, a QuestionAnswerAdvisor queries the vector 
                    database for documents related to the user question.
                    The response from the vector database is appended to the user text to provide 
                    context for the AI model to generate a response. Assuming you have already 
                    loaded data into a VectorStore, you can perform Retrieval Augmented Generation 
                    (RAG) by providing an instance of QuestionAnswerAdvisor to the ChatClient.
                    """,
                     Map.of("prompt", "How does QuestionAnswer Advisor work?"))
        );
}
