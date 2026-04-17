package com.eaglepoint.console.service;

import com.eaglepoint.console.config.SecurityConfig;
import com.eaglepoint.console.exception.ConflictException;
import com.eaglepoint.console.exception.NotFoundException;
import com.eaglepoint.console.exception.ValidationException;
import com.eaglepoint.console.model.*;
import com.eaglepoint.console.repository.BedRepository;
import com.eaglepoint.console.security.EncryptionUtil;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BedService {
    private final BedRepository bedRepo;
    private final EncryptionUtil encryptionUtil;
    private final AuditService auditService;

    public BedService(BedRepository bedRepo, SecurityConfig securityConfig, AuditService auditService) {
        this.bedRepo = bedRepo;
        this.encryptionUtil = new EncryptionUtil(securityConfig.getEncryptionKey());
        this.auditService = auditService;
    }

    public BedBuilding createBuilding(String name, String address, Long serviceAreaId) {
        if (name == null || name.isBlank()) throw new ValidationException("name", "Building name is required");
        BedBuilding b = new BedBuilding();
        b.setName(name.trim());
        b.setAddress(address);
        b.setServiceAreaId(serviceAreaId);
        long id = bedRepo.insertBuilding(b);
        return bedRepo.findBuildingById(id).orElseThrow();
    }

    public PagedResult<BedBuilding> listBuildings(int page, int pageSize) {
        return bedRepo.findAllBuildings(page, pageSize);
    }

    public List<BedRoom> listRooms(long buildingId) {
        bedRepo.findBuildingById(buildingId).orElseThrow(() -> new NotFoundException("BedBuilding", buildingId));
        return bedRepo.findRoomsByBuilding(buildingId);
    }

    public BedRoom createRoom(long buildingId, String roomNumber, Integer floor, String roomType) {
        bedRepo.findBuildingById(buildingId).orElseThrow(() -> new NotFoundException("BedBuilding", buildingId));
        if (roomNumber == null || roomNumber.isBlank()) throw new ValidationException("roomNumber", "Room number is required");
        BedRoom r = new BedRoom();
        r.setBuildingId(buildingId);
        r.setRoomNumber(roomNumber.trim());
        r.setFloor(floor);
        r.setRoomType(roomType);
        long id = bedRepo.insertRoom(r);
        return bedRepo.findRoomById(id).orElseThrow();
    }

    public List<Bed> listBedsInRoom(long roomId) {
        bedRepo.findRoomById(roomId).orElseThrow(() -> new NotFoundException("BedRoom", roomId));
        return bedRepo.findBedsByRoom(roomId);
    }

    public Bed createBed(long roomId, String bedLabel) {
        bedRepo.findRoomById(roomId).orElseThrow(() -> new NotFoundException("BedRoom", roomId));
        if (bedLabel == null || bedLabel.isBlank() || bedLabel.length() > 20) {
            throw new ValidationException("bedLabel", "Bed label must be 1-20 characters");
        }
        Bed b = new Bed();
        b.setRoomId(roomId);
        b.setBedLabel(bedLabel.trim());
        b.setState(BedState.AVAILABLE);
        long id = bedRepo.insertBed(b);
        return bedRepo.findBedById(id).orElseThrow();
    }

    public PagedResult<Bed> listAllBeds(int page, int pageSize, String stateFilter) {
        return bedRepo.findAllBeds(page, pageSize, stateFilter);
    }

    public Bed getBed(long id) {
        return bedRepo.findBedById(id).orElseThrow(() -> new NotFoundException("Bed", id));
    }

    public Map<String, Object> transitionBed(long bedId, BedState toState, String residentId,
                                               String reason, String notes, long actingUserId, String traceId) {
        Bed bed = bedRepo.findBedById(bedId).orElseThrow(() -> new NotFoundException("Bed", bedId));
        BedState fromState = bed.getState();

        BedStateMachine.validateTransition(fromState, toState);

        if (toState == BedState.OCCUPIED && (residentId == null || residentId.isBlank())) {
            throw new ValidationException("residentId", "Resident ID is required when admitting to OCCUPIED");
        }

        String residentEnc = null;
        String admittedAt = null;
        if (toState == BedState.OCCUPIED) {
            residentEnc = encryptionUtil.encrypt(residentId);
            admittedAt = Instant.now().toString();
        } else if (fromState == BedState.OCCUPIED && toState == BedState.AVAILABLE) {
            residentEnc = null;
            admittedAt = null;
        }

        bedRepo.updateBedState(bedId, toState, residentEnc, admittedAt);

        BedStateHistory history = new BedStateHistory();
        history.setBedId(bedId);
        history.setFromState(fromState.name());
        history.setToState(toState.name());
        history.setChangedBy(actingUserId);
        history.setReason(reason);
        history.setNotes(notes);
        if (toState == BedState.OCCUPIED) {
            history.setResidentIdEncrypted(residentEnc);
        }
        bedRepo.insertHistory(history);

        auditService.record("Bed", bedId, "TRANSITION:" + fromState + "->" + toState,
            actingUserId, traceId, fromState.name(), toState.name(), notes);

        Bed updated = bedRepo.findBedById(bedId).orElseThrow();
        Map<String, Object> result = new HashMap<>();
        result.put("bed", updated);
        result.put("historyRecord", history);
        return result;
    }

    public List<BedStateHistory> getBedHistory(long bedId) {
        bedRepo.findBedById(bedId).orElseThrow(() -> new NotFoundException("Bed", bedId));
        return bedRepo.findHistoryByBed(bedId);
    }

    public PagedResult<BedStateHistory> getBedHistoryPaged(long bedId, int page, int pageSize) {
        bedRepo.findBedById(bedId).orElseThrow(() -> new NotFoundException("Bed", bedId));
        return bedRepo.findHistoryByBedPaged(bedId, page, pageSize);
    }

    // ─── Building CRUD ───

    public BedBuilding getBuilding(long id) {
        return bedRepo.findBuildingById(id).orElseThrow(() -> new NotFoundException("BedBuilding", id));
    }

    public BedBuilding updateBuilding(long id, String name, String address) {
        getBuilding(id);
        if (name == null || name.isBlank()) throw new ValidationException("name", "Building name is required");
        bedRepo.updateBuilding(id, name.trim(), address);
        return bedRepo.findBuildingById(id).orElseThrow();
    }

    public void deleteBuilding(long id) {
        getBuilding(id);
        bedRepo.deleteBuilding(id);
    }

    // ─── Room CRUD ───

    public BedRoom getRoom(long id) {
        return bedRepo.findRoomById(id).orElseThrow(() -> new NotFoundException("BedRoom", id));
    }

    public PagedResult<BedRoom> listRoomsPaged(Long buildingId, int page, int pageSize) {
        if (buildingId != null) {
            bedRepo.findBuildingById(buildingId)
                .orElseThrow(() -> new NotFoundException("BedBuilding", buildingId));
            return bedRepo.findRoomsByBuildingPaged(buildingId, page, pageSize);
        }
        return bedRepo.findAllRooms(page, pageSize);
    }

    public BedRoom updateRoom(long id, String roomNumber, Integer floor, String roomType) {
        getRoom(id);
        if (roomNumber == null || roomNumber.isBlank())
            throw new ValidationException("roomNumber", "Room number is required");
        bedRepo.updateRoom(id, roomNumber.trim(), floor, roomType);
        return bedRepo.findRoomById(id).orElseThrow();
    }

    public void deleteRoom(long id) {
        getRoom(id);
        bedRepo.deleteRoom(id);
    }

    // ─── Bed extended CRUD ───

    public PagedResult<Bed> listBeds(Long roomId, Long buildingId, int page, int pageSize) {
        if (roomId != null) {
            bedRepo.findRoomById(roomId).orElseThrow(() -> new NotFoundException("BedRoom", roomId));
            return bedRepo.findBedsByRoomPaged(roomId, page, pageSize);
        }
        if (buildingId != null) {
            bedRepo.findBuildingById(buildingId)
                .orElseThrow(() -> new NotFoundException("BedBuilding", buildingId));
            return bedRepo.findBedsByBuildingPaged(buildingId, page, pageSize);
        }
        return bedRepo.findAllBeds(page, pageSize, null);
    }

    public Object getBed(long id, boolean unmask) {
        Bed bed = getBed(id);
        if (!unmask) {
            bed.setResidentIdEncrypted(null);
            return bed;
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", bed.getId());
        result.put("roomId", bed.getRoomId());
        result.put("bedLabel", bed.getBedLabel());
        result.put("state", bed.getState());
        result.put("admittedAt", bed.getAdmittedAt());
        result.put("createdAt", bed.getCreatedAt());
        result.put("updatedAt", bed.getUpdatedAt());
        if (bed.getResidentIdEncrypted() != null) {
            try { result.put("residentId", encryptionUtil.decrypt(bed.getResidentIdEncrypted())); }
            catch (Exception ignored) { result.put("residentId", null); }
        } else {
            result.put("residentId", null);
        }
        return result;
    }

    public Bed updateBed(long id, String bedLabel) {
        getBed(id);
        if (bedLabel == null || bedLabel.isBlank() || bedLabel.length() > 20)
            throw new ValidationException("bedLabel", "Bed label must be 1-20 characters");
        bedRepo.updateBedLabel(id, bedLabel.trim());
        return bedRepo.findBedById(id).orElseThrow();
    }

    public void deleteBed(long id) {
        getBed(id);
        bedRepo.deleteBed(id);
    }
}
