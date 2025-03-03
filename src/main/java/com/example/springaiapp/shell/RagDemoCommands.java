package com.example.springaiapp.shell;

import com.example.springaiapp.service.RagService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class RagDemoCommands {
    
    @Autowired
    private RagService ragService;
    
    @ShellMethod(key = "ask", value = "Ask a question using RAG")
    public String ask(@ShellOption(help = "Your question") String question) {
        return ragService.processQuery(question);
    }

}
