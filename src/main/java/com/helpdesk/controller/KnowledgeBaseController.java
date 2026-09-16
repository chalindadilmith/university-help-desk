package com.helpdesk.controller;

import com.helpdesk.dto.ChatbotReply;
import com.helpdesk.service.ChatbotService;
import com.helpdesk.service.KnowledgeBaseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** UC-F4-01: student self-service search + chatbot. */
@Controller
@RequestMapping("/student/kb")
public class KnowledgeBaseController {

    private static final int FAILED_ATTEMPTS_BEFORE_TICKET_OFFER = 2;
    private static final String SESSION_ATTR_FAILED_COUNT = "chatbotFailedAttempts";

    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatbotService chatbotService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, ChatbotService chatbotService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatbotService = chatbotService;
    }

    @GetMapping
    public String search(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("articles", knowledgeBaseService.searchPublished(keyword));
        model.addAttribute("keyword", keyword);
        return "student/kb";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("article", knowledgeBaseService.getByIdAndIncrementViews(id));
        return "student/kb-article";
    }

    @PostMapping("/chatbot")
    @ResponseBody
    public Map<String, Object> chatbot(@RequestBody Map<String, String> body, HttpSession session) {
        String message = body.get("message");
        ChatbotReply reply = chatbotService.respond(message);

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
}
