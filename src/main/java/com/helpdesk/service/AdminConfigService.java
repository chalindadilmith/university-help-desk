package com.helpdesk.service;

import com.helpdesk.dto.CategoryForm;
import com.helpdesk.dto.PoolForm;
import com.helpdesk.dto.RoutingRuleForm;
import com.helpdesk.entity.JobPool;
import com.helpdesk.entity.RoutingRule;
import com.helpdesk.entity.TicketCategory;
import com.helpdesk.entity.User;

import java.util.List;

public interface AdminConfigService {

    List<TicketCategory> listCategories();
    TicketCategory createCategory(CategoryForm form, User actingAdmin);

    List<JobPool> listPools();
    JobPool createPool(PoolForm form, User actingAdmin);
    void assignStaffToPool(Long poolId, Long staffUserId, User actingAdmin);
    void removeStaffFromPool(Long poolId, Long staffUserId, User actingAdmin);

    /**
     * Adds several existing, active staff accounts to a work-pool at once. Accounts that are
     * already members are skipped. Returns how many were actually added.
     */
    int addStaffToPool(Long poolId, java.util.Collection<Long> staffUserIds, User actingAdmin);

    /** Points an existing ticket category at an existing work-pool (or at none when {@code poolId} is null). */
    void updateCategoryPool(Long categoryId, Long poolId, User actingAdmin);

    /** Changes which existing work-pool an existing routing rule sends tickets to. */
    void updateRoutingRulePool(Long ruleId, Long poolId, User actingAdmin);

    List<RoutingRule> listRoutingRules();

    /** @throws com.helpdesk.exception.BusinessRuleException if a rule already exists for this category+priority. */
    RoutingRule createRoutingRule(RoutingRuleForm form, User actingAdmin);

    void deleteRoutingRule(Long ruleId, User actingAdmin);
}
