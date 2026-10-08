package com.booking.auth.users;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return false if the email is already registered */
    public boolean insert(User user) {
        try {
            jdbc.sql("""
                    insert into app_user (id, email, password_hash, full_name, phone, roles, enabled, created_at, updated_at)
                    values (:id, :email, :hash, :name, :phone, cast(:roles as text[]), :enabled, :at, :at)
                    """)
                    .param("id", user.id())
                    .param("email", user.email())
                    .param("hash", user.passwordHash())
                    .param("name", user.fullName())
                    .param("phone", user.phone())
                    .param("roles", toArrayLiteral(user.roles()))
                    .param("enabled", user.enabled())
                    .param("at", Timestamp.from(user.createdAt()))
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.sql("select * from app_user where email = :email").param("email", email).query(this::map).optional();
    }

    public Optional<User> findById(UUID id) {
        return jdbc.sql("select * from app_user where id = :id").param("id", id).query(this::map).optional();
    }

    public List<User> findAll(int limit, int offset) {
        return jdbc.sql("select * from app_user order by created_at desc limit :limit offset :offset")
                .param("limit", limit).param("offset", offset).query(this::map).list();
    }

    public boolean existsWithRole(Role role) {
        return jdbc.sql("select exists (select 1 from app_user where :role = any(roles))")
                .param("role", role.name()).query(Boolean.class).single();
    }

    private User map(ResultSet rs, int row) throws SQLException {
        Array roles = rs.getArray("roles");
        Set<Role> parsed = Arrays.stream((String[]) roles.getArray()).map(Role::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        return new User(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
                rs.getString("full_name"), rs.getString("phone"), parsed, rs.getBoolean("enabled"), createdAt);
    }

    private static String toArrayLiteral(Set<Role> roles) {
        return roles.stream().map(Role::name).sorted().collect(Collectors.joining(",", "{", "}"));
    }
}
