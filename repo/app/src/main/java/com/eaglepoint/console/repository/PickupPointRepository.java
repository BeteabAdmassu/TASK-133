package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.PickupPoint;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class PickupPointRepository extends BaseRepository {

    public PickupPointRepository(DataSource ds) {
        super(ds);
    }

    private PickupPoint mapRow(ResultSet rs) throws SQLException {
        PickupPoint p = new PickupPoint();
        p.setId(rs.getLong("id"));
        p.setCommunityId(rs.getLong("community_id"));
        p.setServiceAreaId(getNullableLong(rs, "service_area_id"));
        p.setGeozoneId(getNullableLong(rs, "geozone_id"));
        p.setAddressEncrypted(rs.getString("address_encrypted"));
        p.setZipCode(rs.getString("zip_code"));
        p.setStreetRangeStart(rs.getString("street_range_start"));
        p.setStreetRangeEnd(rs.getString("street_range_end"));
        p.setHoursJson(rs.getString("hours_json"));
        p.setCapacity(rs.getInt("capacity"));
        p.setStatus(rs.getString("status"));
        p.setPausedUntil(rs.getString("paused_until"));
        p.setPauseReason(rs.getString("pause_reason"));
        p.setManualOverride(rs.getInt("manual_override") == 1);
        p.setOverrideNotes(rs.getString("override_notes"));
        p.setActiveDate(rs.getString("active_date"));
        p.setCreatedAt(rs.getString("created_at"));
        p.setUpdatedAt(rs.getString("updated_at"));
        return p;
    }

    public Optional<PickupPoint> findById(long id) {
        return queryOne("SELECT * FROM pickup_points WHERE id = ?", this::mapRow, id);
    }

    public PagedResult<PickupPoint> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM pickup_points ORDER BY id",
            "SELECT COUNT(*) FROM pickup_points", this::mapRow, page, pageSize);
    }

    public PagedResult<PickupPoint> findByCommunity(long communityId, int page, int pageSize) {
        return paginate("SELECT * FROM pickup_points WHERE community_id = ? ORDER BY id",
            "SELECT COUNT(*) FROM pickup_points WHERE community_id = ?",
            this::mapRow, page, pageSize, communityId);
    }

    public int countActiveByCommunity(long communityId) {
        return queryOne(
            "SELECT COUNT(*) as cnt FROM pickup_points WHERE community_id = ? AND status = 'ACTIVE'",
            rs -> rs.getInt("cnt"), communityId
        ).orElse(0);
    }

    /**
     * Counts pickup points in a community that are either currently ACTIVE or were
     * activated on {@code today} (regardless of current status). Used to enforce the
     * one-active-per-community-per-calendar-day rule when creating a new pickup point.
     */
    public int countActiveOrActiveTodayByCommunity(long communityId, String today) {
        return queryOne(
            "SELECT COUNT(*) as cnt FROM pickup_points WHERE community_id = ? AND status != 'INACTIVE' AND (status = 'ACTIVE' OR active_date = ?)",
            rs -> rs.getInt("cnt"), communityId, today
        ).orElse(0);
    }

    /**
     * Same as {@link #countActiveOrActiveTodayByCommunity} but excludes pickup point
     * {@code excludeId}. Used when resuming a pickup point to avoid blocking the
     * same point that was already active today from being re-activated.
     */
    public int countActiveOrActiveTodayByCommunityExcluding(long communityId, String today, long excludeId) {
        return queryOne(
            "SELECT COUNT(*) as cnt FROM pickup_points WHERE community_id = ? AND id != ? AND status != 'INACTIVE' AND (status = 'ACTIVE' OR active_date = ?)",
            rs -> rs.getInt("cnt"), communityId, excludeId, today
        ).orElse(0);
    }

    public long insert(PickupPoint p) {
        return insertAndGetId(
            "INSERT INTO pickup_points (community_id, service_area_id, geozone_id, address_encrypted, zip_code, street_range_start, street_range_end, hours_json, capacity, status, manual_override, active_date) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            p.getCommunityId(), p.getServiceAreaId(), p.getGeozoneId(),
            p.getAddressEncrypted(), p.getZipCode(), p.getStreetRangeStart(),
            p.getStreetRangeEnd(), p.getHoursJson(), p.getCapacity(),
            p.getStatus() != null ? p.getStatus() : "ACTIVE",
            p.isManualOverride() ? 1 : 0,
            p.getActiveDate()
        );
    }

    public void update(PickupPoint p) {
        execute(
            "UPDATE pickup_points SET address_encrypted=?, zip_code=?, street_range_start=?, street_range_end=?, hours_json=?, capacity=?, status=?, paused_until=?, pause_reason=?, manual_override=?, override_notes=?, geozone_id=?, active_date=?, updated_at=datetime('now') WHERE id=?",
            p.getAddressEncrypted(), p.getZipCode(), p.getStreetRangeStart(),
            p.getStreetRangeEnd(), p.getHoursJson(), p.getCapacity(), p.getStatus(),
            p.getPausedUntil(), p.getPauseReason(), p.isManualOverride() ? 1 : 0,
            p.getOverrideNotes(), p.getGeozoneId(), p.getActiveDate(), p.getId()
        );
    }

    public void delete(long id) {
        execute("DELETE FROM pickup_points WHERE id = ?", id);
    }

    public List<PickupPoint> findPausedExpired(String now) {
        return queryList(
            "SELECT * FROM pickup_points WHERE status = 'PAUSED' AND paused_until IS NOT NULL AND paused_until < ?",
            this::mapRow, now
        );
    }

    public List<PickupPoint> findByZipCode(String zipCode, long communityId) {
        return queryList(
            "SELECT * FROM pickup_points WHERE community_id = ? AND zip_code = ? AND status = 'ACTIVE' ORDER BY id LIMIT 1",
            this::mapRow, communityId, zipCode
        );
    }

    /**
     * Returns all ACTIVE pickup points in {@code communityId} that have
     * {@code manual_override = 1}, ordered by {@code id ASC}.
     *
     * <p>The caller selects the first result as the override winner
     * (lowest id = deterministic tie-break when multiple rows exist).
     * Because the one-active-per-community-per-day rule allows at most one
     * ACTIVE pickup point per community, in production this list normally
     * has zero or one entry; the ordering is the safety net.</p>
     */
    public List<PickupPoint> findActiveOverridesByCommunity(long communityId) {
        return queryList(
            "SELECT * FROM pickup_points WHERE community_id = ? AND status = 'ACTIVE' AND manual_override = 1 ORDER BY id ASC",
            this::mapRow, communityId
        );
    }
}
