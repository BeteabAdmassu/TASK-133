package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.Review;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class ReviewRepository extends BaseRepository {

    public ReviewRepository(DataSource ds) {
        super(ds);
    }

    private Review mapRow(ResultSet rs) throws SQLException {
        Review r = new Review();
        r.setId(rs.getLong("id"));
        r.setScorecardId(rs.getLong("scorecard_id"));
        r.setReviewerId(rs.getLong("reviewer_id"));
        r.setSecondReviewerId(getNullableLong(rs, "second_reviewer_id"));
        r.setStatus(rs.getString("status"));
        r.setConflictFlagged(rs.getInt("conflict_flagged") == 1);
        r.setRecusalReason(rs.getString("recusal_reason"));
        r.setRecusedAt(rs.getString("recused_at"));
        r.setReviewedAt(rs.getString("reviewed_at"));
        r.setComments(rs.getString("comments"));
        r.setCreatedAt(rs.getString("created_at"));
        r.setUpdatedAt(rs.getString("updated_at"));
        return r;
    }

    public long insert(Review review) {
        return insertAndGetId(
            "INSERT INTO reviews (scorecard_id, reviewer_id, status) VALUES (?,?,?)",
            review.getScorecardId(), review.getReviewerId(), review.getStatus()
        );
    }

    public Optional<Review> findById(long id) {
        return queryOne("SELECT * FROM reviews WHERE id=?", this::mapRow, id);
    }

    public PagedResult<Review> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM reviews ORDER BY created_at DESC",
            "SELECT COUNT(*) FROM reviews", this::mapRow, page, pageSize);
    }

    public void update(Review review) {
        execute(
            "UPDATE reviews SET status=?, conflict_flagged=?, recusal_reason=?, recused_at=?, reviewed_at=?, comments=?, second_reviewer_id=?, updated_at=datetime('now') WHERE id=?",
            review.getStatus(), review.isConflictFlagged() ? 1 : 0,
            review.getRecusalReason(), review.getRecusedAt(),
            review.getReviewedAt(), review.getComments(),
            review.getSecondReviewerId(), review.getId()
        );
    }
}
