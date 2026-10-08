package com.booking.auth.web;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.booking.auth.users.Accounts;
import com.booking.auth.users.Role;
import com.booking.auth.users.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff and driver accounts. Admin only. */
@RestController
@RequestMapping("/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminUserController {

    private final Accounts accounts;
    private final UserRepository users;

    public AdminUserController(Accounts accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 20) String phone,
            @NotEmpty Set<Role> roles) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthController.UserView create(@Valid @RequestBody CreateUserRequest request) {
        return AuthController.UserView.of(
                accounts.create(request.email(), request.password(), request.fullName(), request.phone(), request.roles()));
    }

    @GetMapping
    public List<AuthController.UserView> list(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return users.findAll(limit, offset).stream().map(AuthController.UserView::of).toList();
    }
}
