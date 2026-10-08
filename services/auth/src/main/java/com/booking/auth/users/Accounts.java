package com.booking.auth.users;

import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.booking.auth.tokens.AccessTokens;
import com.booking.auth.tokens.RefreshTokens;
import com.booking.platform.messaging.Outbox;
import com.booking.platform.web.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, sign-in, session refresh and sign-out. */
@Service
public class Accounts {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AccessTokens accessTokens;
    private final RefreshTokens refreshTokens;
    private final Outbox outbox;
    private final Clock clock;
    /** Compared against when the email is unknown, so both paths take the same time. */
    private final String dummyHash;

    public Accounts(UserRepository users, PasswordEncoder passwords, AccessTokens accessTokens,
            RefreshTokens refreshTokens, Outbox outbox, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.outbox = outbox;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public record Session(User user, AccessTokens.Issued access, RefreshTokens.Issued refresh) {
    }

    @Transactional
    public Session register(String email, String password, String fullName, String phone) {
        User user = create(email, password, fullName, phone, Set.of(Role.CUSTOMER));
        return new Session(user, accessTokens.issue(user), refreshTokens.issue(user.id()));
    }

    /** Staff and driver accounts are created by an admin, never through public sign-up. */
    @Transactional
    public User create(String email, String password, String fullName, String phone, Set<Role> roles) {
        PasswordPolicy.check(password);
        User user = new User(UUID.randomUUID(), normalise(email), passwords.encode(password), fullName.strip(),
                phone == null || phone.isBlank() ? null : phone.strip(), roles, true, clock.instant());
        if (!users.insert(user)) {
            throw ApiException.conflict("email_taken", "An account with this email already exists.");
        }
        outbox.append(new UserRegistered(user.id().toString(), user.email(), user.fullName(),
                user.roles().stream().map(Role::name).sorted().toList()));
        return user;
    }

    @Transactional
    public Session login(String email, String password) {
        User user = users.findByEmail(normalise(email)).orElse(null);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !matches || !user.enabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Email or password is incorrect.");
        }
        return new Session(user, accessTokens.issue(user), refreshTokens.issue(user.id()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String refreshToken) {
        RefreshTokens.Rotation rotation = refreshTokens.rotate(refreshToken);
        User user = users.findById(rotation.userId()).filter(User::enabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "account_disabled", "Sign in again."));
        return new Session(user, accessTokens.issue(user), rotation.next());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("user_not_found", "No such user."));
    }

    static String normalise(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
