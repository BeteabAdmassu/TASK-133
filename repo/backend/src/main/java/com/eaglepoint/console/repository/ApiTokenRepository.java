package com.eaglepoint.console.repository;

import com.eaglepoint.console.model.ApiToken;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class ApiTokenRepository extends BaseRepository {

    public ApiTokenRepository(DataSource ds) {
        super(ds);
    }

    private ApiToken mapRow(ResultSet rs) throws SQLException {
        ApiToken t = new ApiToken();
        t.setId(rs.getLong("id"));
        t.setUserId(rs.getLong("user_id"));
        t.setTokenHash(rs.getString("token_hash"));
        t.setExpiresAt(rs.getString("expires_at"));
        t.setCreatedAt(rs.getString("created_at"));
        return t;
    }

    public Optional<ApiToken> findByTokenHash(String tokenHash) {
        return queryOne("SELECT * FROM api_tokens WHERE token_hash = ?", this::mapRow, tokenHash);
    }

    public long insert(ApiToken token) {
        return insertAndGetId(
            "INSERT INTO api_tokens (user_id, token_hash, expires_at) VALUES (?,?,?)",
            token.getUserId(), token.getTokenHash(), token.getExpiresAt()
        );
    }

    public void deleteByUserId(long userId) {
        execute("DELETE FROM api_tokens WHERE user_id = ?", userId);
    }

    public void deleteExpired() {
        execute("DELETE FROM api_tokens WHERE expires_at < datetime('now')");
    }
}
