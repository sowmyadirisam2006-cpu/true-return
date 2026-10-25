
package com.truereturn.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.truereturn.model.User;
import com.truereturn.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // REGISTER: DELIVERY ACCOUNTS ONLY
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody(required = false) User registrationUser) {

        if (registrationUser == null
                || registrationUser.getUsername() == null
                || registrationUser.getUsername().isBlank()
                || registrationUser.getPassword() == null
                || registrationUser.getPassword().isBlank()) {

            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Username and password are required."
            ));
        }

        String username = registrationUser.getUsername().trim();
        String password = registrationUser.getPassword();

        if (username.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Username must not exceed 100 characters."
            ));
        }

        // BCrypt supports at most 72 password bytes.
        if (password.length() < 8
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {

            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Password must be at least 8 characters and no more than 72 UTF-8 bytes."
            ));
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", "Username already exists."
            ));
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));

        // Never accept a role supplied by the public registration request.
        newUser.setRole("DELIVERY");

        try {
            userRepository.save(newUser);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Delivery account created successfully. You can now log in."
            ));

        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", "Username already exists."
            ));
        }
    }

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody(required = false) User loginUser,
            HttpServletRequest request) {

        if (loginUser == null
                || loginUser.getUsername() == null
                || loginUser.getUsername().isBlank()
                || loginUser.getPassword() == null
                || loginUser.getPassword().isBlank()) {

            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Username and password are required."
            ));
        }

        Optional<User> userOptional =
                userRepository.findByUsername(
                        loginUser.getUsername().trim()
                );

        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Invalid username or password."
            ));
        }

        User existingUser = userOptional.get();

        if (existingUser.getPassword() == null
                || !passwordEncoder.matches(
                        loginUser.getPassword(),
                        existingUser.getPassword())) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Invalid username or password."
            ));
        }

        String role = existingUser.getRole();

        if (role == null || role.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "User role is not configured."
            ));
        }

        role = role.trim().toUpperCase(Locale.ROOT);

        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }

        if (!"ADMIN".equals(role) && !"DELIVERY".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "This account has an unsupported role."
            ));
        }

        // Prevent session fixation by rotating an existing session ID.
        HttpSession existingSession = request.getSession(false);

        if (existingSession != null) {
            request.changeSessionId();
        }

        HttpSession session = request.getSession(true);

        session.setAttribute("username", existingUser.getUsername());
        session.setAttribute("role", role);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        existingUser.getUsername(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_" + role)
                        )
                );

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Save authentication so subsequent requests use the session.
        session.setAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Login successful",
                "username", existingUser.getUsername(),
                "role", role
        ));
    }

    // LOGOUT
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request) {

        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logout successful"
        ));
    }
}
