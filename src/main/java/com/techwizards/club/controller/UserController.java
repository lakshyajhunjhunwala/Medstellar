package com.techwizards.club.controller;

import com.techwizards.club.model.User;
import com.techwizards.club.service.PlatformVerificationService;
import com.techwizards.club.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final PlatformVerificationService platformVerificationService;

    public UserController(UserService userService, PlatformVerificationService platformVerificationService) {
        this.userService = userService;
        this.platformVerificationService = platformVerificationService;
    }

    // Login with Email & Password (or fallback to username)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam(required = false) String email,
                                   @RequestParam(required = false) String password,
                                   @RequestParam(required = false) String username) {
        try {
            if (email != null && !email.trim().isEmpty()) {
                User user = userService.loginWithEmail(email, password != null ? password : "");
                return ResponseEntity.ok(user);
            } else if (username != null && !username.trim().isEmpty()) {
                User user = userService.loginOrRegister(username, email);
                return ResponseEntity.ok(user);
            } else {
                return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required."));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        }
    }

    // Get profile data
    @GetMapping("/{username}")
    public ResponseEntity<User> getProfile(@PathVariable String username) {
        return userService.getProfile(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update external coding profile handles (GitHub/LeetCode)
    @PostMapping("/{username}/profiles")
    public ResponseEntity<User> updateProfiles(@PathVariable String username,
                                               @RequestParam String github,
                                               @RequestParam String leetcode) {
        return userService.updateProfiles(username, github, leetcode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Sync activity from Github/Leetcode (Simulated)
    @PostMapping("/{username}/sync")
    public ResponseEntity<User> syncActivity(@PathVariable String username) {
        return userService.syncActivity(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update full profile and platform handles
    @PostMapping("/{username}/full-profile")
    public ResponseEntity<User> updateFullProfile(@PathVariable String username,
                                                  @RequestParam(required = false) String fullName,
                                                  @RequestParam(required = false) String academicYear,
                                                  @RequestParam(required = false) String statusEmoji,
                                                  @RequestParam(required = false) String bio,
                                                  @RequestParam(required = false) String avatarUrl,
                                                  @RequestParam(required = false) String github,
                                                  @RequestParam(required = false) String leetcode,
                                                  @RequestParam(required = false) String codechef,
                                                  @RequestParam(required = false) String codeforces,
                                                  @RequestParam(required = false) String gfg) {
        return userService.updateFullProfile(username, fullName, academicYear, statusEmoji, bio, avatarUrl,
                        github, leetcode, codechef, codeforces, gfg)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Sync all platforms and award EXP
    @PostMapping("/{username}/sync-platforms")
    public ResponseEntity<User> syncPlatforms(@PathVariable String username) {
        return userService.syncPlatformActivity(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Fetch leaderboard
    @GetMapping("/leaderboard")
    public ResponseEntity<List<User>> getLeaderboard() {
        return ResponseEntity.ok(userService.getLeaderboard());
    }

    // Verification Token & Status
    @GetMapping("/{username}/verify-token")
    public ResponseEntity<?> getVerifyToken(@PathVariable String username) {
        return userService.getProfile(username)
                .map(user -> ResponseEntity.ok(Map.of(
                        "username", user.getUsername(),
                        "token", platformVerificationService.getOrCreateToken(user),
                        "leetcodeVerified", user.getLeetcodeVerified(),
                        "codechefVerified", user.getCodechefVerified(),
                        "codeforcesVerified", user.getCodeforcesVerified(),
                        "gfgVerified", user.getGfgVerified()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // Verify Platform Handle via Bio Inspection
    @PostMapping("/{username}/verify-platform")
    public ResponseEntity<?> verifyPlatform(@PathVariable String username,
                                            @RequestParam String platform,
                                            @RequestParam(defaultValue = "false") boolean demoBypass) {
        return ResponseEntity.ok(platformVerificationService.verifyPlatform(username, platform, demoBypass));
    }

    // Announcements
    @GetMapping("/announcements")
    public ResponseEntity<List<com.techwizards.club.model.Announcement>> getAnnouncements() {
        return ResponseEntity.ok(userService.getAllAnnouncements());
    }

    @PostMapping("/announcements")
    public ResponseEntity<com.techwizards.club.model.Announcement> createAnnouncement(@RequestBody com.techwizards.club.model.Announcement announcement) {
        return ResponseEntity.ok(userService.createAnnouncement(announcement));
    }

    // Member Registration (Sign Up)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestParam String fullName,
                                       @RequestParam String email,
                                       @RequestParam String password,
                                       @RequestParam(required = false) String bio,
                                       @RequestParam(required = false) String academicYear) {
        try {
            User newUser = userService.registerNewUser(fullName, email, password, bio, academicYear);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Registration submitted successfully! Your account is pending review by Administrator (Lakshya).",
                    "user", newUser
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Get all pending applicants for Control Center
    @GetMapping("/pending")
    public ResponseEntity<List<User>> getPendingUsers() {
        return ResponseEntity.ok(userService.getPendingUsers());
    }

    // Approve applicant as official member
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveUser(@PathVariable Long id,
                                         @RequestParam(required = false) String adminUsername) {
        try {
            User user = userService.approveUser(id, adminUsername);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Member approved successfully! " + user.getFullName() + " is now an active club member.",
                    "user", user
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Reject or remove applicant
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectUser(@PathVariable Long id,
                                        @RequestParam(required = false) String adminUsername) {
        boolean removed = userService.removeUser(id, adminUsername);
        if (removed) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Application removed successfully."));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id,
                                        @RequestParam(required = false) String adminUsername) {
        boolean removed = userService.removeUser(id, adminUsername);
        if (removed) {
            return ResponseEntity.ok(Map.of("success", true, "message", "User removed successfully."));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}


