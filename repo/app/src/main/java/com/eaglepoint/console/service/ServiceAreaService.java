package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.ServiceArea;
import com.eaglepoint.console.repository.CommunityRepository;
import com.eaglepoint.console.repository.ServiceAreaRepository;

public class ServiceAreaService {
    private final ServiceAreaRepository serviceAreaRepo;
    private final CommunityRepository communityRepo;
    private final AuditService auditService;

    public ServiceAreaService(ServiceAreaRepository serviceAreaRepo, CommunityRepository communityRepo,
                               AuditService auditService) {
        this.serviceAreaRepo = serviceAreaRepo;
        this.communityRepo = communityRepo;
        this.auditService = auditService;
    }

    public ServiceArea createServiceArea(long communityId, String name, String description) {
        communityRepo.findById(communityId)
            .orElseThrow(() -> new NotFoundException("Community", communityId));
        validateName(name);
        if (serviceAreaRepo.findByNameAndCommunity(name.trim(), communityId).isPresent()) {
            throw new ConflictException("A service area with this name already exists in this community");
        }
        ServiceArea sa = new ServiceArea();
        sa.setCommunityId(communityId);
        sa.setName(name.trim());
        sa.setDescription(description);
        sa.setStatus("ACTIVE");
        long id = serviceAreaRepo.insert(sa);
        return serviceAreaRepo.findById(id).orElseThrow();
    }

    public ServiceArea getServiceArea(long id) {
        return serviceAreaRepo.findById(id).orElseThrow(() -> new NotFoundException("ServiceArea", id));
    }

    public PagedResult<ServiceArea> listServiceAreas(int page, int pageSize) {
        return serviceAreaRepo.findAll(page, pageSize);
    }

    public ServiceArea updateServiceArea(long id, String name, String description, String status) {
        ServiceArea sa = serviceAreaRepo.findById(id).orElseThrow(() -> new NotFoundException("ServiceArea", id));
        if (name != null) {
            validateName(name);
            if (!name.trim().equals(sa.getName()) &&
                serviceAreaRepo.findByNameAndCommunity(name.trim(), sa.getCommunityId()).isPresent()) {
                throw new ConflictException("A service area with this name already exists in this community");
            }
            sa.setName(name.trim());
        }
        if (description != null) sa.setDescription(description);
        if (status != null) {
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw new ValidationException("status", "Status must be ACTIVE or INACTIVE");
            }
            sa.setStatus(status);
        }
        serviceAreaRepo.update(sa);
        return serviceAreaRepo.findById(id).orElseThrow();
    }

    public void deleteServiceArea(long id) {
        if (serviceAreaRepo.findById(id).isEmpty()) {
            throw new NotFoundException("ServiceArea", id);
        }
        serviceAreaRepo.delete(id);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new ValidationException("name", "Service area name must be 1-100 characters");
        }
    }
}
