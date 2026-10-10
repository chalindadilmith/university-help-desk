package com.helpdesk.controller;

import com.helpdesk.dto.ChatTurn;
import com.helpdesk.dto.ChatbotReply;
import com.helpdesk.entity.KbArticle;
import com.helpdesk.service.ChatbotService;
import com.helpdesk.service.KnowledgeBaseService;
import com.helpdesk.security.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** UC-F4-01: student self-service search + chatbot. */
@Controller
@RequestMapping("/student/kb")
public class KnowledgeBaseController {

    private static final int FAILED_ATTEMPTS_BEFORE_TICKET_OFFER = 2;
    private static final String SESSION_ATTR_FAILED_COUNT = "chatbotFailedAttempts";
    private static final String SESSION_ATTR_HISTORY = "chatbotHistory";
    private static final String SESSION_ATTR_RECENT = "chatbotRecentMessages";
    private static final int MAX_HISTORY_TURNS = 12; // messages kept (6 exchanges)
    private static final int MAX_MESSAGES_PER_WINDOW = 15;
    private static final long WINDOW_MS = 5 * 60 * 1000L;
    private static final int MAX_MESSAGE_CHARS = 1000;
    private static final int MAX_MESSAGES_PER_USER_PER_HOUR = 60;
    private static final long USER_WINDOW_MS = 60 * 60 * 1000L;

    private final Map<Long, Deque<Long>> userMessages = new ConcurrentHashMap<>();
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatbotService chatbotService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, ChatbotService chatbotService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatbotService = chatbotService;
    }

    @GetMapping
    public String search(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) Long categoryId,
                         Model model, HttpSession session) {
        model.addAttribute("articles", knowledgeBaseService.searchPublished(keyword, categoryId));
        model.addAttribute("keyword", keyword);
        model.addAttribute("categories", knowledgeBaseService.listCategories());
        model.addAttribute("selectedCategoryId", categoryId);
        @SuppressWarnings("unchecked")
        List<ChatTurn> history = (List<ChatTurn>) session.getAttribute(SESSION_ATTR_HISTORY);
        model.addAttribute("chatHistory", history == null ? List.of() : history);
        return "student/kb";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        // a page shown right after a vote is not a new view
        KbArticle article = model.containsAttribute("voteMessage")
                ? knowledgeBaseService.getById(id)
                : knowledgeBaseService.getByIdAndIncrementViews(id);
        model.addAttribute("article", article);
        model.addAttribute("related", knowledgeBaseService.relatedArticles(article, 3));
        return "student/kb-article";
    }

    /** "Was this helpful?" - one vote per article per login session. */
    @PostMapping("/{id}/vote")
    public String vote(@PathVariable Long id, @RequestParam boolean helpful, HttpSession session,
                       org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        @SuppressWarnings("unchecked")
        java.util.Set<Long> voted = (java.util.Set<Long>) session.getAttribute("kbVoted");
        if (voted == null) {
            voted = new java.util.HashSet<>();
        }
        if (voted.add(id)) {
            knowledgeBaseService.vote(id, helpful);
            session.setAttribute("kbVoted", voted);
            redirectAttributes.addFlashAttribute("voteMessage", "Thanks for your feedback.");
        } else {
            redirectAttributes.addFlashAttribute("voteMessage", "You have already rated this article.");
        }
        return "redirect:/student/kb/" + id;
    }

    @PostMapping("/chatbot")
    @ResponseBody
    public Map<String, Object> chatbot(@RequestBody Map<String, String> body, HttpSession session,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        String message = body.get("message");
        if (message != null && message.length() > MAX_MESSAGE_CHARS) {
            message = message.substring(0, MAX_MESSAGE_CHARS);
        }

        if (isRateLimited(session) || isUserRateLimited(principal)) {
            return Map.of("reply", "You're sending messages very quickly - please wait a few minutes and try again.",
                    "offerTicket", false);
        }

        @SuppressWarnings("unchecked")
        List<ChatTurn> history = (List<ChatTurn>) session.getAttribute(SESSION_ATTR_HISTORY);
        if (history == null) {
            history = new ArrayList<>();
        }
        ChatbotReply reply = chatbotService.respond(message, history, principal == null ? null : principal.getUser());

        if (message != null && !message.isBlank() && reply.reply() != null) {
            history.add(new ChatTurn("user", message.strip()));
            history.add(new ChatTurn("assistant", reply.reply()));
            while (history.size() > MAX_HISTORY_TURNS) {
                history.remove(0);
                history.remove(0);
            }
            session.setAttribute(SESSION_ATTR_HISTORY, history);
        }

        Object failedCountAttr = session.getAttribute(SESSION_ATTR_FAILED_COUNT);
        int failedCount = (failedCountAttr != null) ? (int) failedCountAttr : 0;

        boolean offerTicket = false;
        if (reply.matched()) {
            failedCount = 0;
        } else {
            failedCount++;
            if (failedCount >= FAILED_ATTEMPTS_BEFORE_TICKET_OFFER) {
                offerTicket = true;
                failedCount = 0;
            }
        }
        session.setAttribute(SESSION_ATTR_FAILED_COUNT, failedCount);

        return Map.of("reply", reply.reply(), "offerTicket", offerTicket);
    }

    /** "Clear chat" button: forgets the conversation and the failed-attempt counter. */
    @PostMapping("/chatbot/clear")
    @ResponseBody
    public Map<String, Object> clearChat(HttpSession session) {
        session.removeAttribute(SESSION_ATTR_HISTORY);
        session.removeAttribute(SESSION_ATTR_FAILED_COUNT);
        return Map.of("cleared", true);
    }

    /**
     * At most MAX_MESSAGES_PER_USER_PER_HOUR chatbot messages per student per hour, counted across
     * all of that student's sessions (the per-session limit alone is bypassed by logging in again).
     */
    private boolean isUserRateLimited(CustomUserDetails principal) {
        if (principal == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Deque<Long> times = userMessages.computeIfAbsent(principal.getUser().getId(), k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > USER_WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= MAX_MESSAGES_PER_USER_PER_HOUR) {
                return true;
            }
            times.addLast(now);
            return false;
        }
    }

    /** At most MAX_MESSAGES_PER_WINDOW chatbot messages per session per 5 minutes (protects the AI quota). */
    @SuppressWarnings("unchecked")
    private boolean isRateLimited(HttpSession session) {
        long now = System.currentTimeMillis();
        List<Long> recent = (List<Long>) session.getAttribute(SESSION_ATTR_RECENT);
        if (recent == null) recent = new ArrayList<>();
        recent.removeIf(t -> now - t > WINDOW_MS);
        if (recent.size() >= MAX_MESSAGES_PER_WINDOW) {
            session.setAttribute(SESSION_ATTR_RECENT, recent);
            return true;
        }
        recent.add(now);
        session.setAttribute(SESSION_ATTR_RECENT, recent);
        return false;
    }
}
