package com.techwizards.club.service;

import com.techwizards.club.model.Quiz;
import com.techwizards.club.model.User;
import com.techwizards.club.repository.QuizRepository;
import com.techwizards.club.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final UserRepository userRepository;

    public QuizService(QuizRepository quizRepository, UserRepository userRepository) {
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
    }

    public List<String> getLanguages() {
        return Arrays.asList("JavaScript", "Python", "Java", "C++");
    }

    public List<Quiz> getQuizzesByLanguage(String language) {
        return quizRepository.findByLanguage(language);
    }

    public Map<String, Object> submitAnswer(Long id, String username, String answer) {
        Map<String, Object> response = new HashMap<>();

        Optional<Quiz> quizOpt = quizRepository.findById(id);
        if (quizOpt.isEmpty()) {
            response.put("error", "Quiz not found");
            return response;
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            response.put("error", "User not found");
            return response;
        }

        Quiz quiz = quizOpt.get();
        User user = userOpt.get();
        boolean isCorrect = quiz.getCorrectAnswer().equalsIgnoreCase(answer.trim());

        response.put("correct", isCorrect);
        response.put("correctAnswer", quiz.getCorrectAnswer());

        if (isCorrect) {
            int reward = quiz.getPointsReward();
            user.setPoints(user.getPoints() + reward);

            // Handle Streak increments
            LocalDate today = LocalDate.now();
            if (user.getLastSolvedDate() == null) {
                user.setStreak(1);
            } else if (!user.getLastSolvedDate().equals(today)) {
                // If they solved yesterday, streak increments. If before, reset to 1.
                if (user.getLastSolvedDate().equals(today.minusDays(1))) {
                    user.setStreak(user.getStreak() + 1);
                } else {
                    user.setStreak(1);
                }
            }
            // If they already solved today, streak remains unchanged but they get points.
            user.setLastSolvedDate(today);
            user.updateRank();
            userRepository.save(user);

            response.put("pointsAwarded", reward);
            response.put("newPoints", user.getPoints());
            response.put("newStreak", user.getStreak());
            response.put("newRank", user.getRankName());
        } else {
            response.put("pointsAwarded", 0);
            response.put("newPoints", user.getPoints());
            response.put("newStreak", user.getStreak());
            response.put("newRank", user.getRankName());
        }

        return response;
    }
}
