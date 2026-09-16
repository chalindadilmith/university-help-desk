package com.helpdesk.service.impl;

import com.helpdesk.dto.CategoryForm;
import com.helpdesk.dto.PoolForm;
import com.helpdesk.dto.RoutingRuleForm;
import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.RoutingRule;
import com.helpdesk.entity.TicketCategory;
import com.helpdesk.entity.User;
import com.helpdesk.exception.BusinessRuleException;
import com.helpdesk.exception.DuplicateResourceException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.repository.JobPoolRepository;
import com.helpdesk.repository.RoutingRuleRepository;
import com.helpdesk.repository.TicketCategoryRepository;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.service.AdminConfigService;
import com.helpdesk.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminConfigServiceImpl implements AdminConfigService {

    private final TicketCategoryRepository categoryRepository;
    private final JobPoolRepository poolRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public AdminConfigServiceImpl(TicketCategoryRepository categoryRepository, JobPoolRepository poolRepository,
                                   RoutingRuleRepository routingRuleRepository, UserRepository userRepository,
                                   AuditLogService auditLogService) {
        this.categoryRepository = categoryRepository;
        this.poolRepository = poolRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public List<TicketCategory> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional
    public TicketCategory createCategory(CategoryForm form, User actingAdmin) {
        if (categoryRepository.existsByNameIgnoreCase(form.getName())) {
            throw new DuplicateResourceException("name", "A category with this name already exists.");
        }
        JobPool pool = null;
        if (form.getPoolId() != null) {
            pool = poolRepository.findById(form.getPoolId())
                    .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + form.getPoolId()));
        }
        TicketCategory category = TicketCategory.builder()
                .name(form.getName())
                .description(form.getDescription())
                .pool(pool)
                .build();
        TicketCategory saved = categoryRepository.save(category);
        auditLogService.log(actingAdmin, "CREATE_CATEGORY", "TicketCategory", saved.getId(), saved.getName());
        return saved;
    }

    @Override
    public List<JobPool> listPools() {
        return poolRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional
    public JobPool createPool(PoolForm form, User actingAdmin) {
        if (poolRepository.existsByNameIgnoreCase(form.getName())) {
            throw new DuplicateResourceException("name", "A pool with this name already exists.");
        }
        JobPool pool = JobPool.builder()
                .name(form.getName())
                .description(form.getDescription())
                .build();
        JobPool saved = poolRepository.save(pool);
        auditLogService.log(actingAdmin, "CREATE_POOL", "JobPool", saved.getId(), saved.getName());
        return saved;
    }

    @Override
    @Transactional
    public void assignStaffToPool(Long poolId, Long staffUserId, User actingAdmin) {
        JobPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + staffUserId));
        pool.getStaffMembers().add(staff);
        auditLogService.log(actingAdmin, "ASSIGN_STAFF_TO_POOL", "JobPool", poolId, staff.getEmail());
    }

    @Override
    @Transactional
    public void removeStaffFromPool(Long poolId, Long staffUserId, User actingAdmin) {
        JobPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));
        pool.getStaffMembers().removeIf(u -> u.getId().equals(staffUserId));
        auditLogService.log(actingAdmin, "REMOVE_STAFF_FROM_POOL", "JobPool", poolId, "userId=" + staffUserId);
    }

    @Override
    public List<RoutingRule> listRoutingRules() {
        return routingRuleRepository.findAllByOrderByCategoryNameAsc();
    }

    @Override
    @Transactional
    public RoutingRule createRoutingRule(RoutingRuleForm form, User actingAdmin) {
        TicketCategory category = categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + form.getCategoryId()));
        JobPool pool = poolRepository.findById(form.getTargetPoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + form.getTargetPoolId()));

        // Database-level unique constraint on (category, priority) backs this up too,
        // but checking here lets us show a friendly message instead of a raw SQL error.
        if (routingRuleRepository.existsByCategoryAndPriority(category, form.getPriority())) {
            throw new BusinessRuleException("A routing rule already exists for " + category.getName() +
                    " + " + form.getPriority() + ". Delete it first if you want to change the target pool.");
        }

        RoutingRule rule = RoutingRule.builder()
                .category(category)
                .priority(form.getPriority())
                .targetPool(pool)
                .build();
        RoutingRule saved = routingRuleRepository.save(rule);
        auditLogService.log(actingAdmin, "CREATE_ROUTING_RULE", "RoutingRule", saved.getId(),
                category.getName() + "/" + form.getPriority() + " -> " + pool.getName());
        return saved;
    }

    @Override
    @Transactional
    public void deleteRoutingRule(Long ruleId, User actingAdmin) {
        routingRuleRepository.deleteById(ruleId);
        auditLogService.log(actingAdmin, "DELETE_ROUTING_RULE", "RoutingRule", ruleId, null);
    }
}
