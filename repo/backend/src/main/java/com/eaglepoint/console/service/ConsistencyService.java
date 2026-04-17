package com.eaglepoint.console.service;

import com.eaglepoint.console.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ConsistencyService {
    private static final Logger log = LoggerFactory.getLogger(ConsistencyService.class);
    private final DataSource dataSource;

    public ConsistencyService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> runChecks() {
        List<String> issues = new ArrayList<>();
        issues.addAll(checkOrphanedScorecards());
        issues.addAll(checkBedsWithoutRoom());
        issues.addAll(checkLeaderAssignmentsForInactiveUsers());
        return issues;
    }

    private List<String> checkOrphanedScorecards() {
        List<String> issues = new ArrayList<>();
        String sql = "SELECT s.id FROM scorecards s LEFT JOIN evaluation_cycles ec ON s.cycle_id = ec.id WHERE ec.id IS NULL";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                issues.add("Orphaned scorecard id=" + rs.getLong("id") + " (no parent evaluation cycle)");
            }
        } catch (Exception e) {
            log.error("Error checking orphaned scorecards: {}", e.getMessage());
        }
        return issues;
    }

    private List<String> checkBedsWithoutRoom() {
        List<String> issues = new ArrayList<>();
        String sql = "SELECT b.id FROM beds b LEFT JOIN bed_rooms r ON b.room_id = r.id WHERE r.id IS NULL";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                issues.add("Bed id=" + rs.getLong("id") + " has no parent BedRoom");
            }
        } catch (Exception e) {
            log.error("Error checking beds without room: {}", e.getMessage());
        }
        return issues;
    }

    private List<String> checkLeaderAssignmentsForInactiveUsers() {
        List<String> issues = new ArrayList<>();
        String sql = "SELECT la.id, u.username FROM leader_assignments la " +
                     "JOIN users u ON la.user_id = u.id " +
                     "WHERE la.unassigned_at IS NULL AND u.is_active = 0";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                issues.add("LeaderAssignment id=" + rs.getLong("id") + " references inactive user: " + rs.getString("username"));
            }
        } catch (Exception e) {
            log.error("Error checking leader assignments: {}", e.getMessage());
        }
        return issues;
    }
}
