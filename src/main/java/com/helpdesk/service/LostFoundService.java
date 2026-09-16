package com.helpdesk.service;

import com.helpdesk.dto.ReportItemForm;
import com.helpdesk.entity.ItemType;
import com.helpdesk.entity.LostFoundClaim;
import com.helpdesk.entity.LostFoundItem;
import com.helpdesk.entity.User;

import java.util.List;

public interface LostFoundService {

    LostFoundItem reportItem(User reporter, ReportItemForm form);

    List<LostFoundItem> search(ItemType itemType, String keyword);

    LostFoundItem getById(Long id);

    LostFoundClaim claimItem(User claimant, Long itemId);

    List<LostFoundClaim> pendingClaims();

    List<LostFoundClaim> claimsForItem(Long itemId);

    LostFoundClaim decideClaim(Long claimId, boolean approve, User actingAdmin);
}
