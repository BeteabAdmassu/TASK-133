package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.*;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class EvaluationRepository extends BaseRepository {

    public EvaluationRepository(DataSource ds) {
        super(ds);
    }

    // ─── EvaluationCycle ───

    private EvaluationCycle mapCycle(ResultSet rs) throws SQLException {
        EvaluationCycle c = new EvaluationCycle();
        c.setId(rs.getLong("id"));
        c.setName(rs.getString("name"));
        c.setStartDate(rs.getString("start_date"));
        c.setEndDate(rs.getString("end_date"));
        c.setStatus(rs.getString("status"));
        c.setCreatedBy(rs.getLong("created_by"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setUpdatedAt(rs.getString("updated_at"));
        return c;
    }

    public Optional<EvaluationCycle> findCycleById(long id) {
        return queryOne("SELECT * FROM evaluation_cycles WHERE id = ?", this::mapCycle, id);
    }

    public Optional<EvaluationCycle> findCycleByName(String name) {
        return queryOne("SELECT * FROM evaluation_cycles WHERE name = ?", this::mapCycle, name);
    }

    public PagedResult<EvaluationCycle> findAllCycles(int page, int pageSize) {
        return paginate("SELECT * FROM evaluation_cycles ORDER BY start_date DESC",
            "SELECT COUNT(*) FROM evaluation_cycles", this::mapCycle, page, pageSize);
    }

    public long insertCycle(EvaluationCycle c) {
        return insertAndGetId(
            "INSERT INTO evaluation_cycles (name, start_date, end_date, status, created_by) VALUES (?,?,?,?,?)",
            c.getName(), c.getStartDate(), c.getEndDate(),
            c.getStatus() != null ? c.getStatus() : "DRAFT", c.getCreatedBy()
        );
    }

    public void updateCycle(EvaluationCycle c) {
        execute(
            "UPDATE evaluation_cycles SET name=?, start_date=?, end_date=?, status=?, updated_at=datetime('now') WHERE id=?",
            c.getName(), c.getStartDate(), c.getEndDate(), c.getStatus(), c.getId()
        );
    }

    public void deleteCycle(long id) {
        execute("DELETE FROM evaluation_cycles WHERE id = ?", id);
    }

    public List<EvaluationCycle> findCyclesForArchival(String cutoffDate) {
        return queryList(
            "SELECT * FROM evaluation_cycles WHERE status = 'CLOSED' AND end_date < ?",
            this::mapCycle, cutoffDate
        );
    }

    // ─── ScorecardTemplate ───

    private ScorecardTemplate mapTemplate(ResultSet rs) throws SQLException {
        ScorecardTemplate t = new ScorecardTemplate();
        t.setId(rs.getLong("id"));
        t.setCycleId(rs.getLong("cycle_id"));
        t.setName(rs.getString("name"));
        t.setType(rs.getString("type"));
        t.setCreatedAt(rs.getString("created_at"));
        t.setUpdatedAt(rs.getString("updated_at"));
        return t;
    }

    public Optional<ScorecardTemplate> findTemplateById(long id) {
        return queryOne("SELECT * FROM scorecard_templates WHERE id = ?", this::mapTemplate, id);
    }

    public List<ScorecardTemplate> findTemplatesByCycle(long cycleId) {
        return queryList("SELECT * FROM scorecard_templates WHERE cycle_id = ? ORDER BY id",
            this::mapTemplate, cycleId);
    }

    public long insertTemplate(ScorecardTemplate t) {
        return insertAndGetId(
            "INSERT INTO scorecard_templates (cycle_id, name, type) VALUES (?,?,?)",
            t.getCycleId(), t.getName(), t.getType()
        );
    }

    // ─── ScorecardMetric ───

    private ScorecardMetric mapMetric(ResultSet rs) throws SQLException {
        ScorecardMetric m = new ScorecardMetric();
        m.setId(rs.getLong("id"));
        m.setTemplateId(rs.getLong("template_id"));
        m.setName(rs.getString("name"));
        m.setDescription(rs.getString("description"));
        m.setWeight(rs.getDouble("weight"));
        m.setMaxScore(rs.getDouble("max_score"));
        m.setCreatedAt(rs.getString("created_at"));
        m.setUpdatedAt(rs.getString("updated_at"));
        return m;
    }

    public List<ScorecardMetric> findMetricsByTemplate(long templateId) {
        return queryList("SELECT * FROM scorecard_metrics WHERE template_id = ? ORDER BY id",
            this::mapMetric, templateId);
    }

    public Optional<ScorecardMetric> findMetricById(long id) {
        return queryOne("SELECT * FROM scorecard_metrics WHERE id = ?", this::mapMetric, id);
    }

    public long insertMetric(ScorecardMetric m) {
        return insertAndGetId(
            "INSERT INTO scorecard_metrics (template_id, name, description, weight, max_score) VALUES (?,?,?,?,?)",
            m.getTemplateId(), m.getName(), m.getDescription(), m.getWeight(), m.getMaxScore()
        );
    }

    public void updateMetric(ScorecardMetric m) {
        execute(
            "UPDATE scorecard_metrics SET name=?, description=?, weight=?, max_score=?, updated_at=datetime('now') WHERE id=?",
            m.getName(), m.getDescription(), m.getWeight(), m.getMaxScore(), m.getId()
        );
    }

    public void deleteMetric(long id) {
        execute("DELETE FROM scorecard_metrics WHERE id = ?", id);
    }

    public double sumWeightsByTemplate(long templateId) {
        return queryOne(
            "SELECT COALESCE(SUM(weight), 0) as total FROM scorecard_metrics WHERE template_id = ?",
            rs -> rs.getDouble("total"), templateId
        ).orElse(0.0);
    }

    // ─── Scorecard ───

    private Scorecard mapScorecard(ResultSet rs) throws SQLException {
        Scorecard s = new Scorecard();
        s.setId(rs.getLong("id"));
        s.setCycleId(rs.getLong("cycle_id"));
        s.setTemplateId(rs.getLong("template_id"));
        s.setEvaluateeId(rs.getLong("evaluatee_id"));
        s.setEvaluatorId(rs.getLong("evaluator_id"));
        s.setType(rs.getString("type"));
        s.setStatus(rs.getString("status"));
        s.setSubmittedAt(rs.getString("submitted_at"));
        s.setCreatedAt(rs.getString("created_at"));
        s.setUpdatedAt(rs.getString("updated_at"));
        return s;
    }

    public Optional<Scorecard> findScorecardById(long id) {
        return queryOne("SELECT * FROM scorecards WHERE id = ?", this::mapScorecard, id);
    }

    public PagedResult<Scorecard> findAllScorecards(int page, int pageSize) {
        return paginate("SELECT * FROM scorecards ORDER BY id",
            "SELECT COUNT(*) FROM scorecards", this::mapScorecard, page, pageSize);
    }

    public long insertScorecard(Scorecard s) {
        return insertAndGetId(
            "INSERT INTO scorecards (cycle_id, template_id, evaluatee_id, evaluator_id, type, status) VALUES (?,?,?,?,?,?)",
            s.getCycleId(), s.getTemplateId(), s.getEvaluateeId(),
            s.getEvaluatorId(), s.getType(), s.getStatus() != null ? s.getStatus() : "PENDING"
        );
    }

    public void updateScorecardStatus(long id, String status, String submittedAt) {
        execute(
            "UPDATE scorecards SET status=?, submitted_at=?, updated_at=datetime('now') WHERE id=?",
            status, submittedAt, id
        );
    }

    // ─── ScorecardResponse ───

    private ScorecardResponse mapResponse(ResultSet rs) throws SQLException {
        ScorecardResponse r = new ScorecardResponse();
        r.setId(rs.getLong("id"));
        r.setScorecardId(rs.getLong("scorecard_id"));
        r.setMetricId(rs.getLong("metric_id"));
        r.setScore(rs.getDouble("score"));
        r.setComments(rs.getString("comments"));
        r.setCreatedAt(rs.getString("created_at"));
        r.setUpdatedAt(rs.getString("updated_at"));
        return r;
    }

    public void upsertResponse(ScorecardResponse r) {
        execute(
            "INSERT INTO scorecard_responses (scorecard_id, metric_id, score, comments) VALUES (?,?,?,?) " +
            "ON CONFLICT(scorecard_id, metric_id) DO UPDATE SET score=excluded.score, comments=excluded.comments, updated_at=datetime('now')",
            r.getScorecardId(), r.getMetricId(), r.getScore(), r.getComments()
        );
    }

    public List<ScorecardResponse> findResponsesByScorecard(long scorecardId) {
        return queryList("SELECT * FROM scorecard_responses WHERE scorecard_id = ? ORDER BY metric_id",
            this::mapResponse, scorecardId);
    }

    // ─── Review ───

    private Review mapReview(ResultSet rs) throws SQLException {
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

    public Optional<Review> findReviewById(long id) {
        return queryOne("SELECT * FROM reviews WHERE id = ?", this::mapReview, id);
    }

    public List<Review> findReviewsByScorecard(long scorecardId) {
        return queryList("SELECT * FROM reviews WHERE scorecard_id = ? ORDER BY id",
            this::mapReview, scorecardId);
    }

    public PagedResult<Review> findAllReviews(int page, int pageSize) {
        return paginate("SELECT * FROM reviews ORDER BY id",
            "SELECT COUNT(*) FROM reviews", this::mapReview, page, pageSize);
    }

    public long insertReview(Review r) {
        return insertAndGetId(
            "INSERT INTO reviews (scorecard_id, reviewer_id, status) VALUES (?,?,?)",
            r.getScorecardId(), r.getReviewerId(), r.getStatus() != null ? r.getStatus() : "PENDING"
        );
    }

    public void updateReview(Review r) {
        execute(
            "UPDATE reviews SET status=?, conflict_flagged=?, recusal_reason=?, recused_at=?, reviewed_at=?, comments=?, second_reviewer_id=?, updated_at=datetime('now') WHERE id=?",
            r.getStatus(), r.isConflictFlagged() ? 1 : 0, r.getRecusalReason(),
            r.getRecusedAt(), r.getReviewedAt(), r.getComments(),
            r.getSecondReviewerId(), r.getId()
        );
    }

    // ─── Appeal ───

    private Appeal mapAppeal(ResultSet rs) throws SQLException {
        Appeal a = new Appeal();
        a.setId(rs.getLong("id"));
        a.setScorecardId(rs.getLong("scorecard_id"));
        a.setFiledBy(rs.getLong("filed_by"));
        a.setFiledAt(rs.getString("filed_at"));
        a.setDeadline(rs.getString("deadline"));
        a.setReason(rs.getString("reason"));
        a.setStatus(rs.getString("status"));
        a.setResolvedAt(rs.getString("resolved_at"));
        a.setResolutionNotes(rs.getString("resolution_notes"));
        a.setCreatedAt(rs.getString("created_at"));
        a.setUpdatedAt(rs.getString("updated_at"));
        return a;
    }

    public Optional<Appeal> findAppealById(long id) {
        return queryOne("SELECT * FROM appeals WHERE id = ?", this::mapAppeal, id);
    }

    public List<Appeal> findAppealsByScorecard(long scorecardId) {
        return queryList("SELECT * FROM appeals WHERE scorecard_id = ? ORDER BY filed_at DESC",
            this::mapAppeal, scorecardId);
    }

    public PagedResult<Appeal> findAllAppeals(int page, int pageSize) {
        return paginate("SELECT * FROM appeals ORDER BY filed_at DESC",
            "SELECT COUNT(*) FROM appeals", this::mapAppeal, page, pageSize);
    }

    public long insertAppeal(Appeal a) {
        return insertAndGetId(
            "INSERT INTO appeals (scorecard_id, filed_by, filed_at, deadline, reason, status) VALUES (?,?,datetime('now'),?,?,?)",
            a.getScorecardId(), a.getFiledBy(), a.getDeadline(), a.getReason(),
            a.getStatus() != null ? a.getStatus() : "PENDING"
        );
    }

    public void updateAppeal(Appeal a) {
        execute(
            "UPDATE appeals SET status=?, resolved_at=?, resolution_notes=?, updated_at=datetime('now') WHERE id=?",
            a.getStatus(), a.getResolvedAt(), a.getResolutionNotes(), a.getId()
        );
    }

    public List<Appeal> findOpenAppealsByScorecard(long scorecardId) {
        return queryList(
            "SELECT * FROM appeals WHERE scorecard_id = ? AND status IN ('PENDING','UNDER_REVIEW')",
            this::mapAppeal, scorecardId
        );
    }
}
