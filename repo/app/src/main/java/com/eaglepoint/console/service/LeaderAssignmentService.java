package com.eaglepoint.console.service;

import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.model.LeaderAssignment;
import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.repository.LeaderAssignmentRepository;
import com.eaglepoint.console.repository.ServiceAreaRepository;
import com.eaglepoint.console.repository.UserRepository;

import java.time.Instant;

public class LeaderAssignmentService {
    private final LeaderAssignmentRepository assignmentRepo;
    private final UserRepository userRepo;
    private final ServiceAreaRepository serviceAreaRepo;
    private final AuditService auditService;

    public LeaderAssignmentService(LeaderAssignmentRepository assignmentRepo, UserRepository userRepo,
                                    ServiceAreaRepository serviceAreaRepo, AuditService auditService) {
        this.assignmentRepo = assignmentRepo;
        this.userRepo = userRepo;
        this.serviceAreaRepo = serviceAreaRepo;
        this.auditService = auditService;
    }

    public LeaderAssignment assignLeader(long serviceAreaId, long userId, long assignedBy) {
        serviceAreaRepo.findById(serviceAreaId).orElseThrow(() -> new NotFoundException("ServiceArea", serviceAreaId));
        userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));

        LeaderAssignment la = new LeaderAssignment();
        la.setServiceAreaId(serviceAreaId);
        la.setUserId(userId);
        la.setAssignedBy(assignedBy);

        long id = assignmentRepo.insert(la);
        auditService.record("LeaderAssignment", id, "ASSIGN", assignedBy, null, null, null, null);
        return assignmentRepo.findById(id).orElseThrow();
    }

    public LeaderAssignment endAssignment(long assignmentId, long actingUserId) {
        LeaderAssignment la = assignmentRepo.findById(assignmentId)
            .orElseThrow(() -> new NotFoundException("LeaderAssignment", assignmentId));
        String now = Instant.now().toString();
        assignmentRepo.endAssignment(assignmentId, now);
        auditService.record("LeaderAssignment", assignmentId, "END", actingUserId, null, null, null, null);
        return assignmentRepo.findById(assignmentId).orElseThrow();
    }

    public PagedResult<LeaderAssignment> listAssignments(int page, int pageSize) {
        return assignmentRepo.findAll(page, pageSize);
    }
}
