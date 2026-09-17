package com.techwizards.club.controller;

import com.techwizards.club.model.Quiz;
import com.techwizards.club.service.QuizService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quizzes")
@CrossOrigin(origins = "*")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    // Get list of unique quiz languages
    @GetMapping("/languages")
    public ResponseEntity<List<String>> getLanguages() {
        return ResponseEntity.ok(quizService.getLanguages());
    }

    // Get all quizzes for a specific language
    @GetMapping
    public ResponseEntity<List<Quiz>> getQuizzesByLanguage(@RequestParam String language) {
        return ResponseEntity.ok(quizService.getQuizzesByLanguage(language));
    }

    // Submit answer and reward user
    @PostMapping("/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitAnswer(@PathVariable Long id,
                                                            @RequestParam String username,
                                                            @RequestParam String answer) {
        Map<String, Object> response = quizService.submitAnswer(id, username, answer);
        
        if (response.containsKey("error")) {
            String error = (String) response.get("error");
            if ("Quiz not found".equals(error)) {
                return ResponseEntity.notFound().build();
            } else if ("User not found".equals(error)) {
                return ResponseEntity.badRequest().body(response);
            }
        }
        
        return ResponseEntity.ok(response);
    }
}

