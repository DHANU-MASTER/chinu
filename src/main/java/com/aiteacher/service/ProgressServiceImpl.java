package com.aiteacher.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aiteacher.dto.ProgressSaveRequest;
import com.aiteacher.dto.ProgressSummary;
import com.aiteacher.entity.LearningSession;
import com.aiteacher.repository.LearningSessionRepository;

/**
 * Phase 11 implementation of progress tracking.
 * Stores and retrieves actual learning session data.
 * All progress calculations are based on real stored data.
 */
@Service
public class ProgressServiceImpl implements ProgressService {

    private static final Logger log = LoggerFactory.getLogger(ProgressServiceImpl.class);

    private final LearningSessionRepository sessionRepository;

    public ProgressServiceImpl(LearningSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public LearningSession saveSession(ProgressSaveRequest request) {
        LearningSession session = LearningSession.builder()
                .studentName(request.getStudentName())
                .topic(request.getTopic())
                .language(request.getLanguage())
                .educationalLevel(request.getEducationalLevel())
                .teachingStyle(request.getTeachingStyle())
                .learningObjective(request.getLearningObjective())
                .score(request.getScore())
                .totalQuestions(request.getTotalQuestions())
                .percentage(request.getPercentage())
                .conceptsUnderstood(request.getConceptsUnderstood() != null
                        ? new ArrayList<>(request.getConceptsUnderstood()) : new ArrayList<>())
                .weakAreas(request.getWeakAreas() != null
                        ? new ArrayList<>(request.getWeakAreas()) : new ArrayList<>())
                .incorrectConcepts(request.getIncorrectConcepts() != null
                        ? new ArrayList<>(request.getIncorrectConcepts()) : new ArrayList<>())
                .revisionRecommendations(request.getRevisionRecommendations() != null
                        ? new ArrayList<>(request.getRevisionRecommendations()) : new ArrayList<>())
                .suggestedNextTopic(request.getSuggestedNextTopic())
                .completedAt(LocalDateTime.now())
                .build();

        LearningSession saved = sessionRepository.save(session);
        log.info("Saved learning session for student '{}' on topic '{}' with score {}/{} ({}%)",
                saved.getStudentName(), saved.getTopic(),
                saved.getScore(), saved.getTotalQuestions(), saved.getPercentage());
        return saved;
    }

    @Override
    public List<LearningSession> getStudentHistory(String studentName) {
        return sessionRepository.findByStudentNameOrderByCompletedAtDesc(studentName);
    }

    @Override
    public ProgressSummary getProgressSummary(String studentName) {
        List<LearningSession> sessions = sessionRepository.findByStudentNameOrderByCompletedAtDesc(studentName);

        if (sessions.isEmpty()) {
            return ProgressSummary.builder()
                    .totalLessonsCompleted(0)
                    .averageScore(0.0)
                    .bestScore(0)
                    .topicsStudied(new ArrayList<>())
                    .weakConcepts(new ArrayList<>())
                    .strengths(new ArrayList<>())
                    .recentActivity(new ArrayList<>())
                    .latestRecommendations(new ArrayList<>())
                    .latestNextTopic(null)
                    .build();
        }

        // Calculate total lessons
        int totalLessons = sessions.size();

        // Calculate average score
        double averageScore = sessions.stream()
                .mapToDouble(LearningSession::getPercentage)
                .average()
                .orElse(0.0);

        // Find best score
        int bestScore = (int) sessions.stream()
                .mapToDouble(LearningSession::getPercentage)
                .max()
                .orElse(0.0);

        // Get all topics studied
        List<String> topicsStudied = sessions.stream()
                .map(LearningSession::getTopic)
                .distinct()
                .collect(Collectors.toList());

        // Calculate weak concepts (appear in multiple sessions)
        Map<String, Long> weakConceptCounts = new HashMap<>();
        for (LearningSession session : sessions) {
            if (session.getWeakAreas() != null) {
                for (String weak : session.getWeakAreas()) {
                    weakConceptCounts.merge(weak, 1L, Long::sum);
                }
            }
        }
        List<String> weakConcepts = weakConceptCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1) // Concepts weak in multiple sessions
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // If no repeated weak concepts, show all unique weak areas
        if (weakConcepts.isEmpty()) {
            weakConcepts = weakConceptCounts.keySet().stream()
                    .collect(Collectors.toList());
        }

        // Calculate strengths (concepts understood in multiple sessions)
        Map<String, Long> strengthCounts = new HashMap<>();
        for (LearningSession session : sessions) {
            if (session.getConceptsUnderstood() != null) {
                for (String concept : session.getConceptsUnderstood()) {
                    strengthCounts.merge(concept, 1L, Long::sum);
                }
            }
        }
        List<String> strengths = strengthCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1) // Concepts understood in multiple sessions
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // If no repeated strengths, show top understood concepts
        if (strengths.isEmpty()) {
            strengths = strengthCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        // Get recent activity (last 5 sessions)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        List<ProgressSummary.RecentActivity> recentActivity = sessions.stream()
                .limit(5)
                .map(session -> ProgressSummary.RecentActivity.builder()
                        .topic(session.getTopic())
                        .score(session.getScore())
                        .percentage(session.getPercentage())
                        .completedAt(session.getCompletedAt().format(formatter))
                        .status(getStatusFromScore(session.getPercentage()))
                        .build())
                .collect(Collectors.toList());

        // Get latest recommendations
        List<String> latestRecommendations = sessions.stream()
                .filter(s -> s.getRevisionRecommendations() != null && !s.getRevisionRecommendations().isEmpty())
                .findFirst()
                .map(LearningSession::getRevisionRecommendations)
                .orElse(new ArrayList<>());

        // Get latest next topic
        String latestNextTopic = sessions.stream()
                .filter(s -> s.getSuggestedNextTopic() != null && !s.getSuggestedNextTopic().isBlank())
                .findFirst()
                .map(LearningSession::getSuggestedNextTopic)
                .orElse(null);

        return ProgressSummary.builder()
                .totalLessonsCompleted(totalLessons)
                .averageScore(Math.round(averageScore * 10.0) / 10.0) // Round to 1 decimal
                .bestScore(bestScore)
                .topicsStudied(topicsStudied)
                .weakConcepts(weakConcepts)
                .strengths(strengths)
                .recentActivity(recentActivity)
                .latestRecommendations(latestRecommendations)
                .latestNextTopic(latestNextTopic)
                .build();
    }

    /**
     * Determines the status string based on the score percentage.
     */
    private String getStatusFromScore(double percentage) {
        if (percentage >= 80) {
            return "Strong";
        } else if (percentage >= 60) {
            return "Good";
        } else if (percentage >= 40) {
            return "Needs Revision";
        } else {
            return "Weak";
        }
    }
}
