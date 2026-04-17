package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.BedRoom;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class BedRoomRepository extends BaseRepository {

    public BedRoomRepository(DataSource ds) {
        super(ds);
    }

    private BedRoom mapRow(ResultSet rs) throws SQLException {
        BedRoom r = new BedRoom();
        r.setId(rs.getLong("id"));
        r.setBuildingId(rs.getLong("building_id"));
        r.setRoomNumber(rs.getString("room_number"));
        r.setFloor(getNullableInt(rs, "floor"));
        r.setCreatedAt(rs.getString("created_at"));
        r.setUpdatedAt(rs.getString("updated_at"));
        return r;
    }

    public long insert(BedRoom room) {
        return insertAndGetId(
            "INSERT INTO bed_rooms (building_id, room_number, floor) VALUES (?,?,?)",
            room.getBuildingId(), room.getRoomNumber(), room.getFloor()
        );
    }

    public Optional<BedRoom> findById(long id) {
        return queryOne("SELECT * FROM bed_rooms WHERE id=?", this::mapRow, id);
    }

    public PagedResult<BedRoom> findAll(Long buildingId, int page, int pageSize) {
        if (buildingId != null) {
            return paginate("SELECT * FROM bed_rooms WHERE building_id=? ORDER BY room_number",
                "SELECT COUNT(*) FROM bed_rooms WHERE building_id=?", this::mapRow, page, pageSize, buildingId);
        }
        return paginate("SELECT * FROM bed_rooms ORDER BY room_number",
            "SELECT COUNT(*) FROM bed_rooms", this::mapRow, page, pageSize);
    }

    public void update(BedRoom room) {
        execute(
            "UPDATE bed_rooms SET room_number=?, floor=?, updated_at=datetime('now') WHERE id=?",
            room.getRoomNumber(), room.getFloor(), room.getId()
        );
    }

    public void delete(long id) {
        execute("DELETE FROM bed_rooms WHERE id=?", id);
    }
}
