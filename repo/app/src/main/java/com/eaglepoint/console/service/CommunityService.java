package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.Community;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.repository.CommunityRepository;

public class CommunityService {
    private final CommunityRepository communityRepo;
    private final AuditService auditService;

    public CommunityService(CommunityRepository communityRepo, AuditService auditService) {
        this.communityRepo = communityRepo;
        this.auditService = auditService;
    }

    public Community createCommunity(String name, String description) {
        validateName(name);
        if (communityRepo.findByName(name.trim()).isPresent()) {
            throw new ConflictException("A community with this name already exists.");
        }
        Community c = new Community();
        c.setName(name.trim());
        c.setDescription(description);
        c.setStatus("ACTIVE");
        long id = communityRepo.insert(c);
        return communityRepo.findById(id).orElseThrow();
    }

    public Community getCommunity(long id) {
        return communityRepo.findById(id).orElseThrow(() -> new NotFoundException("Community", id));
    }

    public PagedResult<Community> listCommunities(int page, int pageSize) {
        return communityRepo.findAll(page, pageSize);
    }

    public Community updateCommunity(long id, String name, String description, String status) {
        Community c = communityRepo.findById(id).orElseThrow(() -> new NotFoundException("Community", id));
        if (name != null) {
            validateName(name);
            if (!name.trim().equals(c.getName()) && communityRepo.findByName(name.trim()).isPresent()) {
                throw new ConflictException("A community with this name already exists.");
            }
            c.setName(name.trim());
        }
        if (description != null) c.setDescription(description);
        if (status != null) {
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw new ValidationException("status", "Status must be ACTIVE or INACTIVE");
            }
            c.setStatus(status);
        }
        auditService.record("Community", id, "UPDATE", 0, null, null, c, null);
        communityRepo.update(c);
        return communityRepo.findById(id).orElseThrow();
    }

    public void deleteCommunity(long id) {
        if (communityRepo.findById(id).isEmpty()) {
            throw new NotFoundException("Community", id);
        }
        if (communityRepo.hasServiceAreas(id)) {
            throw new ConflictException("Cannot delete community with existing service areas");
        }
        communityRepo.delete(id);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new ValidationException("name", "Community name must be 1-100 characters");
        }
    }
}
