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
}
