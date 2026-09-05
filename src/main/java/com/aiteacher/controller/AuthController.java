package com.aiteacher.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiteacher.dto.AuthResponse;
import com.aiteacher.dto.LoginRequest;
import com.aiteacher.dto.RegisterRequest;
import com.aiteacher.entity.UserAccount;
import com.aiteacher.repository.UserAccountRepository;

/**
 * Controller for student authentication (Registration, Login, Profile Picture update, Password Reset).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository userRepository;

    public AuthController(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * POST /api/auth/register — Registers a new student account.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (isBlank(request.getName())) {
            return badRequest("Name must not be empty.");
        }
        if (isBlank(request.getEmail())) {
            return badRequest("Email must not be empty.");
        }
        if (isBlank(request.getPassword())) {
            return badRequest("Password must not be empty.");
        }
        if (request.getPassword().length() < 4) {
            return badRequest("Password must be at least 4 characters long.");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "An account with this email already exists. Please log in."));
        }

        String avatar = isBlank(request.getProfilePic()) ? "🧑‍🎓" : request.getProfilePic().trim();

        UserAccount user = UserAccount.builder()
                .name(request.getName().trim())
                .email(email)
                .passwordHash(hashPassword(request.getPassword().trim()))
                .profilePic(avatar)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        AuthResponse response = AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profilePic(user.getProfilePic())
                .message("Account registered successfully! Please log in.")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/login — Authenticates a student with Email + Password.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (isBlank(request.getEmail())) {
            return badRequest("Email must not be empty.");
        }
        if (isBlank(request.getPassword())) {
            return badRequest("Password must not be empty.");
        }

        String email = request.getEmail().trim().toLowerCase();
        Optional<UserAccount> userOpt = userRepository.findByEmailIgnoreCase(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password. Please check your credentials."));
        }

        UserAccount user = userOpt.get();
        if (!user.getPasswordHash().equals(hashPassword(request.getPassword().trim()))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password. Please check your credentials."));
        }

        String avatar = isBlank(user.getProfilePic()) ? "🧑‍🎓" : user.getProfilePic();

        AuthResponse response = AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profilePic(avatar)
                .message("Logged in successfully!")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/profile/update — Updates user profile picture, name, or resets password.
     */
    @PostMapping("/profile/update")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (isBlank(email)) {
            return badRequest("Email is required.");
        }

        Optional<UserAccount> userOpt = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Student account not found."));
        }

        UserAccount user = userOpt.get();
        if (payload.containsKey("name") && !isBlank(payload.get("name"))) {
            user.setName(payload.get("name").trim());
        }
        if (payload.containsKey("profilePic") && !isBlank(payload.get("profilePic"))) {
            user.setProfilePic(payload.get("profilePic").trim());
        }

        // Reset password if newPassword is provided
        if (payload.containsKey("newPassword") && !isBlank(payload.get("newPassword"))) {
            String newPass = payload.get("newPassword").trim();
            if (newPass.length() < 4) {
                return badRequest("New password must be at least 4 characters long.");
            }
            user.setPasswordHash(hashPassword(newPass));
        }

        userRepository.save(user);

        AuthResponse response = AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profilePic(user.getProfilePic())
                .message("Profile updated successfully!")
                .build();

        return ResponseEntity.ok(response);
    }

    private static String hashPassword(String password) {
        return Integer.toHexString(password.hashCode());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
