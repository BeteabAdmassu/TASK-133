package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.BedStateHistory;
import com.eaglepoint.console.model.PagedResult;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class BedStateHistoryRepository extends BaseRepository {

    public BedStateHistoryRepository(DataSource ds) {
        super(ds);
    }

    private BedStateHistory mapRow(ResultSet rs) throws SQLException {
        BedStateHistory h = new BedStateHistory();
        h.setId(rs.getLong("id"));
        h.setBedId(rs.getLong("bed_id"));
        h.setFromState(rs.getString("from_state"));
        h.setToState(rs.getString("to_state"));
        h.setChangedBy(rs.getLong("changed_by"));
        h.setChangedAt(rs.getString("changed_at"));
        h.setReason(rs.getString("reason"));
        h.setNotes(rs.getString("notes"));
        h.setResidentIdEncrypted(rs.getString("resident_id_encrypted"));
        return h;
    }

    public long insert(BedStateHistory history) {
        return insertAndGetId(
            "INSERT INTO bed_state_history (bed_id, from_state, to_state, changed_by, reason, notes, resident_id_encrypted) VALUES (?,?,?,?,?,?,?)",
            history.getBedId(), history.getFromState(), history.getToState(),
            history.getChangedBy(), history.getReason(), history.getNotes(),
            history.getResidentIdEncrypted()
        );
    }

    public Optional<BedStateHistory> findById(long id) {
        return queryOne("SELECT * FROM bed_state_history WHERE id=?", this::mapRow, id);
    }

    public PagedResult<BedStateHistory> findByBedId(long bedId, int page, int pageSize) {
        return paginate(
            "SELECT * FROM bed_state_history WHERE bed_id=? ORDER BY changed_at DESC",
            "SELECT COUNT(*) FROM bed_state_history WHERE bed_id=?",
            this::mapRow, page, pageSize, bedId
        );
    }
}
