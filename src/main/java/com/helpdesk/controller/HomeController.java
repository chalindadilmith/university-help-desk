package com.helpdesk.controller;

import com.helpdesk.entity.KbArticle;
import com.helpdesk.service.KnowledgeBaseService;
import com.helpdesk.util.RoleRedirectResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * "/" and "/faq" are public (see SecurityConfig). A visitor who is already signed in is sent straight
 * to their dashboard; everyone else sees the public home page.
 */
@Controller
public class HomeController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final String universityName;
    private final String hours;
    private final String location;
    private final String email;
    private final String phone;
    private final String responseTime;

    public HomeController(KnowledgeBaseService knowledgeBaseService,
                          @Value("${app.university-name:University}") String universityName,
                          @Value("${app.contact.hours:}") String hours,
                          @Value("${app.contact.location:}") String location,
                          @Value("${app.contact.email:}") String email,
                          @Value("${app.contact.phone:}") String phone,
                          @Value("${app.contact.response-time:}") String responseTime) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.universityName = universityName;
        this.hours = hours;
        this.location = location;
        this.email = email;
        this.phone = phone;
        this.responseTime = responseTime;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        if (isSignedIn(authentication)) {
            return "redirect:" + RoleRedirectResolver.resolve(authentication.getAuthorities());
        }
        addContact(model);
        List<KbArticle> popular = knowledgeBaseService.searchPublished(null);
        model.addAttribute("popularArticles", popular.size() > 5 ? popular.subList(0, 5) : popular);
        return "home";
    }

    @GetMapping("/faq")
    public String faq(@RequestParam(required = false) String keyword, Model model) {
        addContact(model);
        model.addAttribute("keyword", keyword);
        model.addAttribute("articles", knowledgeBaseService.searchPublished(keyword));
        return "faq";
    }

    private static boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private void addContact(Model model) {
        model.addAttribute("universityName", universityName);
        model.addAttribute("contactHours", hours);
        model.addAttribute("contactLocation", location);
        model.addAttribute("contactEmail", email);
        model.addAttribute("contactPhone", phone);
        model.addAttribute("contactResponse", responseTime);
    }
}
