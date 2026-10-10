package com.helpdesk.controller;

import com.helpdesk.dto.ManagedTicketForm;
import com.helpdesk.entity.AccountStatus;
import com.helpdesk.entity.Priority;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.Ticket;
import com.helpdesk.entity.TicketStatus;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.security.CustomUserDetails;
import com.helpdesk.service.AdminConfigService;
import com.helpdesk.service.TicketAttachmentService;
import com.helpdesk.service.TicketService;
import com.helpdesk.service.UserService;
import com.helpdesk.util.TicketListView;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin ticket management: full CRUD on every ticket, who each one is assigned to,
 * and a search over every detail of every ticket.
 */
@Controller
@RequestMapping("/admin/tickets")
public class AdminTicketController {

    private final TicketService ticketService;
    private final TicketAttachmentService attachmentService;
    private final AdminConfigService adminConfigService;
    private final UserService userService;

    public AdminTicketController(TicketService ticketService, TicketAttachmentService attachmentService,
                                 AdminConfigService adminConfigService, UserService userService) {
        this.ticketService = ticketService;
        this.attachmentService = attachmentService;
        this.adminConfigService = adminConfigService;
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String assignee,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String scope,
                       @RequestParam(defaultValue = "1") int page,
                       Model model) {
        var everything = ticketService.allTickets();
        TicketListView view = TicketListView.of(everything, q, status, sort, page, true, scope, priority, assignee, categoryId);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            counts.put(s.name(), everything.stream().filter(t -> t.getStatus() == s).count());
        }
        model.addAttribute("tickets", view.getItems());
        model.addAttribute("view", view);
        model.addAttribute("totalAll", everything.size());
        model.addAttribute("statusCounts", counts);
        model.addAttribute("unassignedCount", everything.stream().filter(t -> t.getAssignedStaff() == null
                && t.getStatus() != TicketStatus.RESOLVED && t.getStatus() != TicketStatus.CLOSED).count());
        model.addAttribute("q", q);
        model.addAttribute("statusFilter", status);
        model.addAttribute("priorityFilter", priority);
        model.addAttribute("categoryFilter", categoryId);
        model.addAttribute("assigneeFilter", assignee);
        model.addAttribute("sort", sort);
        model.addAttribute("scope", scope);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("categories", adminConfigService.listCategories());
        model.addAttribute("staffList", activeStaff());
        return "admin/tickets";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Ticket ticket = ticketService.getById(id);
        model.addAttribute("ticket", ticket);
        model.addAttribute("comments", ticketService.commentsFor(id));
        model.addAttribute("attachments", attachmentService.forTicket(id));
        return "admin/ticket-detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("ticketForm")) {
            model.addAttribute("ticketForm", new ManagedTicketForm());
        }
        populateForm(model, null);
        return "manage/ticket-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("ticketForm") ManagedTicketForm form, BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails principal, Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Ticket created = ticketService.createForStudent(form, principal.getUser());
                redirectAttributes.addFlashAttribute("success", "Ticket " + created.getTicketRef() + " was created.");
                return "redirect:/admin/tickets/" + created.getId();
            } catch (BusinessRuleException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        populateForm(model, null);
        return "manage/ticket-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Ticket ticket = ticketService.getById(id);
        if (!model.containsAttribute("ticketForm")) {
            model.addAttribute("ticketForm", StaffTicketController.toForm(ticket));
        }
        populateForm(model, ticket);
        return "manage/ticket-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @Valid @ModelAttribute("ticketForm") ManagedTicketForm form,
                       BindingResult bindingResult, @AuthenticationPrincipal CustomUserDetails principal,
                       Model model, RedirectAttributes redirectAttributes) {
        Ticket ticket = ticketService.getById(id);
        if (!bindingResult.hasErrors()) {
            try {
                ticketService.updateManaged(id, form, principal.getUser());
                redirectAttributes.addFlashAttribute("success", "Ticket " + ticket.getTicketRef() + " was updated.");
                return "redirect:/admin/tickets/" + id;
            } catch (BusinessRuleException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        populateForm(model, ticket);
        return "manage/ticket-form";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        ticketService.deleteTicket(id, principal.getUser());
        redirectAttributes.addFlashAttribute("success", "The ticket has been deleted.");
        return "redirect:/admin/tickets";
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        ticketService.getById(id);
        return AttachmentResponses.file(attachmentService, attachmentService.get(id, attachmentId));
    }

    // ---------- helpers ----------

    private List<com.helpdesk.entity.User> activeStaff() {
        return userService.findAllStaffAndAdmins().stream()
                .filter(u -> u.getRole() == Role.STAFF && u.getStatus() == AccountStatus.ACTIVE)
                .toList();
    }

    private void populateForm(Model model, Ticket ticket) {
        model.addAttribute("ticket", ticket);
        model.addAttribute("basePath", "/admin/tickets");
        model.addAttribute("canAssign", true);
        model.addAttribute("categories", adminConfigService.listCategories());
        model.addAttribute("priorities", Priority.values());
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("students", ticket == null ? userService.searchUsers(null, Role.STUDENT) : List.of());
        model.addAttribute("staffList", activeStaff());
    }
}
