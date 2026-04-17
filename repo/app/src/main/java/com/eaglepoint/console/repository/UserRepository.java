package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.PagedResult;
import com.eaglepoint.console.model.User;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserRepository extends BaseRepository {

    public UserRepository(DataSource ds) {
        super(ds);
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setDisplayName(rs.getString("display_name"));
        u.setRole(rs.getString("role"));
        u.setStaffIdEncrypted(rs.getString("staff_id_encrypted"));
        u.setActive(rs.getInt("is_active") == 1);
        u.setLastLogin(rs.getString("last_login"));
        u.setCreatedAt(rs.getString("created_at"));
        u.setUpdatedAt(rs.getString("updated_at"));
        return u;
    }

    public Optional<User> findById(long id) {
        return queryOne("SELECT * FROM users WHERE id = ?", this::mapRow, id);
    }

    public Optional<User> findByUsername(String username) {
        return queryOne("SELECT * FROM users WHERE username = ?", this::mapRow, username);
    }

    public PagedResult<User> findAll(int page, int pageSize) {
        return paginate("SELECT * FROM users ORDER BY id",
            "SELECT COUNT(*) FROM users", this::mapRow, page, pageSize);
    }

    public long insert(User u) {
        return insertAndGetId(
            "INSERT INTO users (username, password_hash, display_name, role, staff_id_encrypted, is_active) VALUES (?,?,?,?,?,?)",
            u.getUsername(), u.getPasswordHash(), u.getDisplayName(),
            u.getRole(), u.getStaffIdEncrypted(), u.isActive() ? 1 : 0
        );
    }

    public void update(User u) {
        execute(
            "UPDATE users SET display_name=?, role=?, staff_id_encrypted=?, is_active=?, updated_at=datetime('now') WHERE id=?",
            u.getDisplayName(), u.getRole(), u.getStaffIdEncrypted(), u.isActive() ? 1 : 0, u.getId()
        );
    }

    public void deactivate(long id) {
        execute("UPDATE users SET is_active=0, updated_at=datetime('now') WHERE id=?", id);
    }

    public void updateLastLogin(long id, String timestamp) {
        execute("UPDATE users SET last_login=?, updated_at=datetime('now') WHERE id=?", timestamp, id);
    }
}
