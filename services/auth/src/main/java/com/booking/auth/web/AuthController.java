package com.booking.auth.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.booking.auth.users.Accounts;
import com.booking.auth.users.Role;
import com.booking.auth.users.User;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final Accounts accounts;

    public AuthController(Accounts accounts) {
        this.accounts = accounts;
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "must be a phone number") String phone) {
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    public record UserView(UUID id, String email, String fullName, String phone, List<String> roles) {
        static UserView of(User user) {
            return new UserView(user.id(), user.email(), user.fullName(), user.phone(),
                    user.roles().stream().map(Role::name).sorted().toList());
        }
    }

    public record SessionView(String tokenType, String accessToken, Instant accessTokenExpiresAt,
            String refreshToken, Instant refreshTokenExpiresAt, UserView user) {
        static SessionView of(Accounts.Session session) {
            return new SessionView("Bearer", session.access().token(), session.access().expiresAt(),
                    session.refresh().token(), session.refresh().expiresAt(), UserView.of(session.user()));
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView register(@Valid @RequestBody RegisterRequest request) {
        return SessionView.of(accounts.register(request.email(), request.password(), request.fullName(), request.phone()));
    }

    @PostMapping("/login")
    public SessionView login(@Valid @RequestBody LoginRequest request) {
        return SessionView.of(accounts.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public SessionView refresh(@Valid @RequestBody RefreshRequest request) {
        return SessionView.of(accounts.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        accounts.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return UserView.of(accounts.get(UUID.fromString(jwt.getSubject())));
    }
}
