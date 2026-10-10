package com.helpdesk.service;

import com.helpdesk.dto.ChatbotReply;

public interface ChatbotService {

    /**
     * Simple keyword-matching bot (UC-F4-01): searches published KB articles
     * for the student's message and returns either a direct pointer to the
     * best match or an "I couldn't find anything" reply. Deliberately NOT a
     * real NLP/LLM integration - flagged in Phase 0 as a scope simplification
     * appropriate for a semester project's time budget.
     */
    ChatbotReply respond(String message);

    /**
     * General-purpose reply: when an AI API key is configured, answers any question
     * conversationally (using matching knowledge-base articles as context and the recent
     * conversation history); otherwise, or if the AI call fails, falls back to
     * {@link #respond(String)}. {@code history} is the prior turns, oldest first. {@code student} (may be null) is the signed-in
     * student, whose own tickets / Lost &amp; Found / registration data is given to the AI as context.
     */
    ChatbotReply respond(String message, java.util.List<com.helpdesk.dto.ChatTurn> history, com.helpdesk.entity.User student);
}
