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
    @Transactional
    public int addStaffToPool(Long poolId, java.util.Collection<Long> staffUserIds, User actingAdmin) {
        JobPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));
        if (staffUserIds == null || staffUserIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one staff member to add.");
        }
        int added = 0;
        for (Long id : new java.util.LinkedHashSet<>(staffUserIds)) {
            User staff = userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
            if (staff.getRole() != com.helpdesk.entity.Role.STAFF) {
                throw new BusinessRuleException(staff.getFullName() + " is not a staff account.");
            }
            if (staff.getStatus() != com.helpdesk.entity.AccountStatus.ACTIVE) {
                throw new BusinessRuleException(staff.getFullName() + " is not active, so cannot join a work-pool.");
            }
            if (pool.getStaffMembers().stream().noneMatch(m -> m.getId().equals(staff.getId()))) {
                pool.getStaffMembers().add(staff);
                added++;
                auditLogService.log(actingAdmin, "ASSIGN_STAFF_TO_POOL", "JobPool", poolId,
                        staff.getEmail() + " added to " + pool.getName());
            }
        }
        return added;
    }

    @Override
    @Transactional
    public void updateCategoryPool(Long categoryId, Long poolId, User actingAdmin) {
        TicketCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        JobPool pool = null;
        if (poolId != null) {
            pool = poolRepository.findById(poolId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));
        }
        category.setPool(pool);
        auditLogService.log(actingAdmin, "UPDATE_CATEGORY_POOL", "TicketCategory", categoryId,
                category.getName() + " -> " + (pool == null ? "no pool" : pool.getName()));
    }

    @Override
    @Transactional
    public void updateRoutingRulePool(Long ruleId, Long poolId, User actingAdmin) {
        RoutingRule rule = routingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing rule not found: " + ruleId));
        JobPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("Pool not found: " + poolId));
        rule.setTargetPool(pool);
        auditLogService.log(actingAdmin, "UPDATE_ROUTING_RULE", "RoutingRule", ruleId,
                rule.getCategory().getName() + "/" + rule.getPriority() + " -> " + pool.getName());
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
