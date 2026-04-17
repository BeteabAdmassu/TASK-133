package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.*;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class BedRepository extends BaseRepository {

    public BedRepository(DataSource ds) {
        super(ds);
    }

    // ─── BedBuilding ───

    private BedBuilding mapBuilding(ResultSet rs) throws SQLException {
        BedBuilding b = new BedBuilding();
        b.setId(rs.getLong("id"));
        b.setName(rs.getString("name"));
        b.setAddress(rs.getString("address"));
        b.setServiceAreaId(getNullableLong(rs, "service_area_id"));
        b.setCreatedAt(rs.getString("created_at"));
        b.setUpdatedAt(rs.getString("updated_at"));
        return b;
    }

    public Optional<BedBuilding> findBuildingById(long id) {
        return queryOne("SELECT * FROM bed_buildings WHERE id = ?", this::mapBuilding, id);
    }

    public PagedResult<BedBuilding> findAllBuildings(int page, int pageSize) {
        return paginate("SELECT * FROM bed_buildings ORDER BY name",
            "SELECT COUNT(*) FROM bed_buildings", this::mapBuilding, page, pageSize);
    }

    public long insertBuilding(BedBuilding b) {
        return insertAndGetId(
            "INSERT INTO bed_buildings (name, address, service_area_id) VALUES (?,?,?)",
            b.getName(), b.getAddress(), b.getServiceAreaId()
        );
    }

    // ─── BedRoom ───

    private BedRoom mapRoom(ResultSet rs) throws SQLException {
        BedRoom r = new BedRoom();
        r.setId(rs.getLong("id"));
        r.setBuildingId(rs.getLong("building_id"));
        r.setRoomNumber(rs.getString("room_number"));
        r.setFloor(getNullableInt(rs, "floor"));
        r.setRoomType(rs.getString("room_type"));
        r.setCreatedAt(rs.getString("created_at"));
        r.setUpdatedAt(rs.getString("updated_at"));
        return r;
    }

    public Optional<BedRoom> findRoomById(long id) {
        return queryOne("SELECT * FROM bed_rooms WHERE id = ?", this::mapRoom, id);
    }

    public List<BedRoom> findRoomsByBuilding(long buildingId) {
        return queryList("SELECT * FROM bed_rooms WHERE building_id = ? ORDER BY room_number",
            this::mapRoom, buildingId);
    }

    public long insertRoom(BedRoom r) {
        return insertAndGetId(
            "INSERT INTO bed_rooms (building_id, room_number, floor, room_type) VALUES (?,?,?,?)",
            r.getBuildingId(), r.getRoomNumber(), r.getFloor(), r.getRoomType()
        );
    }

    // ─── Bed ───

    private Bed mapBed(ResultSet rs) throws SQLException {
        Bed b = new Bed();
        b.setId(rs.getLong("id"));
        b.setRoomId(rs.getLong("room_id"));
        b.setBedLabel(rs.getString("bed_label"));
        b.setState(BedState.valueOf(rs.getString("state")));
        b.setResidentIdEncrypted(rs.getString("resident_id_encrypted"));
        b.setAdmittedAt(rs.getString("admitted_at"));
        b.setCreatedAt(rs.getString("created_at"));
        b.setUpdatedAt(rs.getString("updated_at"));
        return b;
    }

    public Optional<Bed> findBedById(long id) {
        return queryOne("SELECT * FROM beds WHERE id = ?", this::mapBed, id);
    }

    public PagedResult<Bed> findAllBeds(int page, int pageSize, String stateFilter) {
        if (stateFilter != null && !stateFilter.isEmpty()) {
            return paginate("SELECT * FROM beds WHERE state = ? ORDER BY id",
                "SELECT COUNT(*) FROM beds WHERE state = ?", this::mapBed, page, pageSize, stateFilter);
        }
        return paginate("SELECT * FROM beds ORDER BY id",
            "SELECT COUNT(*) FROM beds", this::mapBed, page, pageSize);
    }

    public List<Bed> findBedsByRoom(long roomId) {
        return queryList("SELECT * FROM beds WHERE room_id = ? ORDER BY bed_label",
            this::mapBed, roomId);
    }

    public long insertBed(Bed b) {
        return insertAndGetId(
            "INSERT INTO beds (room_id, bed_label, state) VALUES (?,?,?)",
            b.getRoomId(), b.getBedLabel(), b.getState() != null ? b.getState().name() : "AVAILABLE"
        );
    }

    public void updateBedState(long id, BedState state, String residentEnc, String admittedAt) {
        execute(
            "UPDATE beds SET state=?, resident_id_encrypted=?, admitted_at=?, updated_at=datetime('now') WHERE id=?",
            state.name(), residentEnc, admittedAt, id
        );
    }

    // ─── BedStateHistory ───

    private BedStateHistory mapHistory(ResultSet rs) throws SQLException {
        BedStateHistory h = new BedStateHistory();
        h.setId(rs.getLong("id"));
        h.setBedId(rs.getLong("bed_id"));
        h.setFromState(rs.getString("from_state"));
        h.setToState(rs.getString("to_state"));
        h.setChangedBy(rs.getLong("changed_by"));
        h.setChangedAt(rs.getString("changed_at"));
        h.setReason(rs.getString("reason"));
        h.setResidentIdEncrypted(rs.getString("resident_id_encrypted"));
        h.setNotes(rs.getString("notes"));
        return h;
    }

    public long insertHistory(BedStateHistory h) {
        return insertAndGetId(
            "INSERT INTO bed_state_history (bed_id, from_state, to_state, changed_by, changed_at, reason, resident_id_encrypted, notes) VALUES (?,?,?,?,datetime('now'),?,?,?)",
            h.getBedId(), h.getFromState(), h.getToState(), h.getChangedBy(),
            h.getReason(), h.getResidentIdEncrypted(), h.getNotes()
        );
    }

    public List<BedStateHistory> findHistoryByBed(long bedId) {
        return queryList("SELECT * FROM bed_state_history WHERE bed_id = ? ORDER BY changed_at DESC",
            this::mapHistory, bedId);
    }

    public PagedResult<BedStateHistory> findHistoryByBedPaged(long bedId, int page, int pageSize) {
        return paginate(
            "SELECT * FROM bed_state_history WHERE bed_id=? ORDER BY changed_at DESC",
            "SELECT COUNT(*) FROM bed_state_history WHERE bed_id=?",
            this::mapHistory, page, pageSize, bedId);
    }

    // ─── BedBuilding update/delete ───

    public void updateBuilding(long id, String name, String address) {
        execute("UPDATE bed_buildings SET name=?, address=?, updated_at=datetime('now') WHERE id=?",
            name, address, id);
    }

    public void deleteBuilding(long id) {
        execute("DELETE FROM bed_buildings WHERE id=?", id);
    }

    // ─── BedRoom pagination + update/delete ───

    public PagedResult<BedRoom> findAllRooms(int page, int pageSize) {
        return paginate("SELECT * FROM bed_rooms ORDER BY room_number",
            "SELECT COUNT(*) FROM bed_rooms", this::mapRoom, page, pageSize);
    }

    public PagedResult<BedRoom> findRoomsByBuildingPaged(long buildingId, int page, int pageSize) {
        return paginate(
            "SELECT * FROM bed_rooms WHERE building_id=? ORDER BY room_number",
            "SELECT COUNT(*) FROM bed_rooms WHERE building_id=?",
            this::mapRoom, page, pageSize, buildingId);
    }

    public void updateRoom(long id, String roomNumber, Integer floor, String roomType) {
        execute("UPDATE bed_rooms SET room_number=?, floor=?, room_type=?, updated_at=datetime('now') WHERE id=?",
            roomNumber, floor, roomType, id);
    }

    public void deleteRoom(long id) {
        execute("DELETE FROM bed_rooms WHERE id=?", id);
    }

    // ─── Bed update/delete ───

    public void updateBedLabel(long id, String bedLabel) {
        execute("UPDATE beds SET bed_label=?, updated_at=datetime('now') WHERE id=?", bedLabel, id);
    }

    public void deleteBed(long id) {
        execute("DELETE FROM beds WHERE id=?", id);
    }
}
