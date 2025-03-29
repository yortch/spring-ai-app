package com.example.springaiapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * This service demonstrates the Evaluator Optimizer Agent pattern using Spring AI.
 * 
 * The pattern involves multiple AI agents working together to iteratively improve content:
 * 1. Writer Agent - Creates the initial draft and refines based on feedback
 * 2. Editor Agent - Evaluates the draft and provides actionable feedback
 * 
 * This iterative refinement continues until the content is approved or reaches max iterations.
 */
@Service
public class BlogWriterService {
    private static final Logger logger = LoggerFactory.getLogger(BlogWriterService.class);
    private static final int MAX_ITERATIONS = 3;  // Maximum number of refinement iterations

    private final ChatClient chatClient;

    /**
     * Initialize the service with a ChatClient that has SimpleLoggerAdvisor.
     * 
     * The SimpleLoggerAdvisor automatically logs all AI interactions (prompts and responses)
     * when the application's logging level is set to DEBUG for the advisor package.
     * 
     * @param chatClientBuilder Builder for creating a configured ChatClient
     */
    public BlogWriterService(ChatClient.Builder chatClientBuilder) {
        // Add SimpleLoggerAdvisor to log requests and responses for debugging
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        logger.info("BlogWriterService initialized with ChatClient and SimpleLoggerAdvisor");
    }

    /**
     * Enhanced version of generateBlogPost that also returns metadata about the generation process.
     * 
     * This method ensures at least one feedback-improvement cycle occurs to demonstrate
     * the full evaluator-optimizer pattern in action, regardless of initial draft quality.
     * 
     * @param topic The blog post topic
     * @return A BlogGenerationResult containing the content and metadata
     */
    public BlogGenerationResult generateBlogPostWithMetadata(String topic) {
        logger.info("Starting blog generation with metadata for topic: {}", topic);
        
        BlogGenerationResult result = new BlogGenerationResult();
        result.setModelName("Azure OpenAI");
        
        // PHASE 1: WRITER AGENT
        // Prompt the Writer agent to generate the initial blog draft
        String initialPrompt = String.format("""
            You are a professional blog writer. Write a well-structured, engaging blog post about "%s".
            The post should have a clear introduction, body paragraphs, and conclusion.
            Include relevant examples and maintain a conversational yet professional tone.
            
            IMPORTANT FORMATTING REQUIREMENTS:
            1. Format as plain text only (no Markdown, HTML, or special formatting)
            2. Use simple ASCII characters only
            3. For the title, simply put it on the first line and use ALL CAPS instead of "#" symbols
            4. Separate paragraphs with blank lines
            5. The blog post must be concise and contain NO MORE THAN 10 SENTENCES total.
            """, topic);
        
        // Using Spring AI's fluent API to send the prompt and get the response
        logger.info("Sending initial draft generation prompt to AI model");
        String draft = chatClient.prompt()
                .user(initialPrompt)
                .call()
                .content();
        
        // Estimate token usage as we can't directly access it
        estimateTokenUsage(result, initialPrompt, draft);
        logger.info("Initial draft successfully generated for topic: {}", topic);

        // PHASE 2: EVALUATION & REFINEMENT LOOP
        // Setup for the iterative improvement process
        boolean approved = false;
        int iteration = 1;
        boolean forceFirstIteration = true; // Force at least one feedback cycle to demonstrate the pattern
        
        // Continue until we reach max iterations or get approval (but always do at least one iteration)
        while ((!approved && iteration <= MAX_ITERATIONS) || forceFirstIteration) {
            logger.info("Starting iteration {} of blog refinement", iteration);
            
            // PHASE 2A: EDITOR AGENT
            // Prompt the Editor agent to evaluate the current draft
            String evalPrompt = String.format("""
                You are a critical blog editor with extremely high standards. Evaluate the following blog draft and respond with either:
                PASS - if the draft is exceptional, well-written, engaging, and complete
                NEEDS_IMPROVEMENT - followed by specific, actionable feedback on what to improve
                
                Focus on:
                - Clarity and flow of ideas
                - Engagement and reader interest
                - Professional yet conversational tone
                - Structure and organization
                - Strict adherence to the 10-sentence maximum length requirement
                
                IMPORTANT EVALUATION RULES:
                1. The blog MUST have no more than 10 sentences total. Count the sentences carefully.
                2. For the first iteration, ALWAYS respond with NEEDS_IMPROVEMENT regardless of quality.
                3. Be extremely thorough in your evaluation and provide detailed feedback.
                4. If the draft exceeds 10 sentences, it must receive a NEEDS_IMPROVEMENT rating.
                5. Even well-written drafts should receive suggestions for improvement in early iterations.
                
                Draft:
                %s
                """, draft);
            
            // Send the evaluation prompt to the AI model
            logger.info("Sending draft for editorial evaluation (iteration: {})", iteration);
            String evaluation = chatClient.prompt()
                    .user(evalPrompt)
                    .call()
                    .content();
            
            // After first iteration, remove the force flag
            if (forceFirstIteration) {
                forceFirstIteration = false;
            }
            
            estimateTokenUsage(result, evalPrompt, evaluation);
            
            // Check if the Editor agent approves the draft
            if (evaluation.toUpperCase().contains("PASS") && iteration > 1) { // Only allow PASS after first iteration
                // Draft is approved, exit the loop
                approved = true;
                logger.info("Draft approved by editor on iteration {}", iteration);
            } else {
                // Draft needs improvement, extract the specific feedback
                String feedback = extractFeedback(evaluation);
                logger.info("Editor feedback received (iteration {}): {}", iteration, feedback);
                result.addEditorFeedback(feedback);
                
                // PHASE 2B: WRITER AGENT (REFINEMENT)
                // Prompt the Writer agent to refine the draft based on the feedback
                String refinePrompt = String.format("""
                    You are a blog writer. Improve the following blog draft based on this editorial feedback:
                    
                    Feedback: %s
                    
                    Current Draft:
                    %s
                    
                    IMPORTANT REQUIREMENTS:
                    1. The final blog post MUST NOT exceed 10 sentences total.
                    2. Maintain a clear introduction, body, and conclusion structure.
                    3. Keep formatting as plain text only (NO Markdown, HTML, or special formatting)
                    4. For the title, use ALL CAPS instead of any special formatting
                    5. Separate paragraphs with blank lines
                    6. Use only simple ASCII characters
                    7. Provide the complete improved version while addressing the feedback.
                    8. Count your sentences carefully before submitting.
                    """, feedback, draft);
                
                // Send the refinement prompt to the AI model
                logger.info("Requesting draft revision based on feedback (iteration: {})", iteration);
                String revisedDraft = chatClient.prompt()
                        .user(refinePrompt)
                        .call()
                        .content();
                
                estimateTokenUsage(result, refinePrompt, revisedDraft);
                draft = revisedDraft;
                logger.info("Revised draft received for iteration {}", iteration);
            }
            iteration++;
        }

        // PHASE 3: FINALIZATION
        // Set final result properties
        result.setContent(draft);
        result.setApproved(approved);
        result.setIterations(iteration - 1);
        
        if (!approved) {
            logger.warn("Maximum iterations ({}) reached without editor approval", MAX_ITERATIONS);
        } else {
            logger.info("Blog post generation completed successfully for topic: {}", topic);
        }

        return result;
    }

    /**
     * Helper method to extract actionable feedback from the Editor agent's evaluation.
     * This extracts the text after "NEEDS_IMPROVEMENT" to get just the feedback portion.
     * 
     * @param evaluation The full evaluation text from the Editor agent
     * @return Just the actionable feedback portion
     */
    private String extractFeedback(String evaluation) {
        if (evaluation == null) return "";
        int idx = evaluation.toUpperCase().indexOf("NEEDS_IMPROVEMENT");
        if (idx != -1) {
            // Return text after "NEEDS_IMPROVEMENT"
            return evaluation.substring(idx + "NEEDS_IMPROVEMENT".length()).trim();
        }
        return evaluation;
    }
    
    /**
     * Helper method to estimate token usage as we can't directly access it
     * This is a rough estimation: approximately 4 characters per token
     */
    private void estimateTokenUsage(BlogGenerationResult result, String prompt, String response) {
        try {
            // Very rough estimation: ~4 characters per token
            int estimatedPromptTokens = prompt.length() / 4;
            int estimatedCompletionTokens = response.length() / 4;
            
            result.addPromptTokens(estimatedPromptTokens);
            result.addCompletionTokens(estimatedCompletionTokens);
            
            logger.debug("Estimated token usage: prompt={}, completion={}, total={}",
                estimatedPromptTokens, estimatedCompletionTokens, 
                estimatedPromptTokens + estimatedCompletionTokens);
        } catch (Exception e) {
            logger.warn("Failed to estimate token usage", e);
        }
    }
    
    /**
     * Class to hold blog generation result, including the content and metadata.
     */
    public static class BlogGenerationResult {
        private String content;
        private int iterations;
        private boolean approved;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private String modelName;
        private List<String> editorFeedback = new ArrayList<>();
        
        // Getters and setters
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public int getIterations() {
            return iterations;
        }
        
        public void setIterations(int iterations) {
            this.iterations = iterations;
        }
        
        public boolean isApproved() {
            return approved;
        }
        
        public void setApproved(boolean approved) {
            this.approved = approved;
        }
        
        public int getPromptTokens() {
            return promptTokens;
        }
        
        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            this.totalTokens = this.promptTokens + this.completionTokens;
        }
        
        public void addPromptTokens(int tokens) {
            this.promptTokens += tokens;
            this.totalTokens = this.promptTokens + this.completionTokens;
        }
        
        public int getCompletionTokens() {
            return completionTokens;
        }
        
        public void setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            this.totalTokens = this.promptTokens + this.completionTokens;
        }
        
        public void addCompletionTokens(int tokens) {
            this.completionTokens += tokens;
            this.totalTokens = this.promptTokens + this.completionTokens;
        }
        
        public int getTotalTokens() {
            return totalTokens;
        }
        
        public String getModelName() {
            return modelName;
        }
        
        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
        
        public List<String> getEditorFeedback() {
            return editorFeedback;
        }
        
        public void setEditorFeedback(List<String> editorFeedback) {
            this.editorFeedback = editorFeedback;
        }
        
        public void addEditorFeedback(String feedback) {
            if (this.editorFeedback == null) {
                this.editorFeedback = new ArrayList<>();
            }
            this.editorFeedback.add(feedback);
        }
    }
}