package com.example.springaiapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class BlogWriterService {
    private static final Logger logger = LoggerFactory.getLogger(BlogWriterService.class);
    private static final int MAX_ITERATIONS = 3;

    private final ChatClient chatClient;

    public BlogWriterService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateBlogPost(String topic) {
        String initialPrompt = String.format("""
            You are a professional blog writer. Write a well-structured, engaging blog post about "%s".
            The post should have a clear introduction, body paragraphs, and conclusion.
            Include relevant examples and maintain a conversational yet professional tone.
            """, topic);
        
        String draft = chatClient.prompt(initialPrompt).call().chatResponse().getResult().getOutput().getText();

        boolean approved = false;
        int iteration = 1;        
        while (!approved && iteration <= MAX_ITERATIONS) {
            String evalPrompt = String.format("""
                You are a critical blog editor. Evaluate the following blog draft and respond with either:
                PASS - if the draft is well-written, engaging, and complete
                NEEDS_IMPROVEMENT - followed by specific, actionable feedback on what to improve
                
                Focus on:
                - Clarity and flow of ideas
                - Engagement and reader interest
                - Professional yet conversational tone
                - Structure and organization
                
                Draft:
                %s
                """, draft);
            
            String evaluation = chatClient.prompt(evalPrompt).call()
                .chatResponse().getResult().getOutput().getText();

            if (evaluation.toUpperCase().contains("PASS")) {
                approved = true;
                logger.info("Draft approved by editor on iteration {}", iteration);
            } else {
                String feedback = extractFeedback(evaluation);
                logger.info("Editor feedback (iteration {}): {}", iteration, feedback);
                
                String refinePrompt = String.format("""
                    You are a blog writer. Improve the following blog draft based on this editorial feedback:
                    
                    Feedback: %s
                    
                    Current Draft:
                    %s
                    
                    Provide the complete improved version while maintaining the original topic and structure.
                    """, feedback, draft);
                
                draft = chatClient.prompt(refinePrompt).call()
                    .chatResponse().getResult().getOutput().getText();
            }
            iteration++;
        }

        if (!approved) {
            logger.warn("Maximum iterations ({}) reached without editor approval", MAX_ITERATIONS);
        }
        
        return draft;
    }

    private String extractFeedback(String evaluation) {
        if (evaluation == null) return "";
        int idx = evaluation.toUpperCase().indexOf("NEEDS_IMPROVEMENT");
        if (idx != -1) {
            return evaluation.substring(idx + "NEEDS_IMPROVEMENT".length()).trim();
        }
        return evaluation;
    }
}