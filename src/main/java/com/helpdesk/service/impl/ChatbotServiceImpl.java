package com.helpdesk.service.impl;

import com.helpdesk.dto.ChatbotReply;
import com.helpdesk.entity.KbArticle;
import com.helpdesk.entity.KbArticleStatus;
import com.helpdesk.repository.KbArticleRepository;
import com.helpdesk.service.ChatbotService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatbotServiceImpl implements ChatbotService {

    private final KbArticleRepository articleRepository;

    public ChatbotServiceImpl(KbArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Override
    public ChatbotReply respond(String message) {
        if (message == null || message.isBlank()) {
            return new ChatbotReply("Ask me something about IT, academics, facilities, or anything else you'd normally raise a ticket for.", false);
        }

        // Naive but effective for a small KB: try progressively shorter keyword
        // slices of the message against title/content, take the first hit.
        String cleaned = message.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim();
        String[] words = cleaned.split("\\s+");

        List<KbArticle> matches = articleRepository.search(KbArticleStatus.PUBLISHED, cleaned);
        if (matches.isEmpty() && words.length > 1) {
            // fall back to searching by the single longest word (likely the most specific term)
            String longestWord = "";
            for (String w : words) {
                if (w.length() > longestWord.length()) longestWord = w;
            }
            matches = articleRepository.search(KbArticleStatus.PUBLISHED, longestWord);
        }

        if (!matches.isEmpty()) {
            KbArticle best = matches.get(0);
            String snippet = best.getContent().length() > 160 ? best.getContent().substring(0, 160) + "..." : best.getContent();
            return new ChatbotReply("I found this article: \"" + best.getTitle() + "\" - " + snippet, true);
        }

        return new ChatbotReply("I couldn't find anything in the knowledge base about that.", false);
    }
}
