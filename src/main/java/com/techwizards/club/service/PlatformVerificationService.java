package com.techwizards.club.service;

import com.techwizards.club.model.User;
import com.techwizards.club.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PlatformVerificationService {

    private final UserRepository userRepository;
    private final HttpClient httpClient;

    public PlatformVerificationService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String getOrCreateToken(User user) {
        if (user.getVerificationToken() != null && !user.getVerificationToken().trim().isEmpty()) {
            return user.getVerificationToken();
        }
        // Generate a clean token like MS-4892
        int randomCode = 1000 + (int)(Math.random() * 9000);
        String token = "MS-" + randomCode;
        user.setVerificationToken(token);
        userRepository.save(user);
        return token;
    }

    public Map<String, Object> verifyPlatform(String username, String platform, boolean demoBypass) {
        Map<String, Object> response = new HashMap<>();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found");
            return response;
        }

        User user = userOpt.get();
        String token = getOrCreateToken(user);
        String p = (platform != null) ? platform.trim().toLowerCase() : "";

        String handle = "";
        switch (p) {
            case "leetcode":
                handle = user.getLeetcodeUsername();
                break;
            case "codechef":
                handle = user.getCodechefUsername();
                break;
            case "codeforces":
                handle = user.getCodeforcesUsername();
                break;
            case "gfg":
            case "geeksforgeeks":
                handle = user.getGfgUsername();
                break;
            default:
                response.put("success", false);
                response.put("message", "Unsupported platform: " + platform);
                return response;
        }

        if (handle == null || handle.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Please configure your " + platform + " handle in Edit Profile first.");
            return response;
        }

        boolean verified = false;
        String platformName = p.toUpperCase();

        if (demoBypass) {
            verified = true;
        } else {
            try {
                if ("leetcode".equals(p)) {
                    verified = checkLeetCodeBio(handle, token);
                    platformName = "LeetCode";
                } else if ("codeforces".equals(p)) {
                    verified = checkCodeforcesBio(handle, token);
                    platformName = "Codeforces";
                } else if ("codechef".equals(p)) {
                    verified = checkUrlContainsToken("https://www.codechef.com/users/" + handle, token);
                    platformName = "CodeChef";
                } else if ("gfg".equals(p) || "geeksforgeeks".equals(p)) {
                    verified = checkUrlContainsToken("https://auth.geeksforgeeks.org/user/" + handle + "/", token);
                    platformName = "GeeksforGeeks";
                }
            } catch (Exception e) {
                // If external network call fails or times out, inform the user
                System.err.println("Verification network check error: " + e.getMessage());
                response.put("success", false);
                response.put("message", "Could not reach " + platform + " servers (" + e.getMessage() + "). You can use Instant Verify for quick testing.");
                return response;
            }
        }

        if (verified) {
            // Apply verification
            switch (p) {
                case "leetcode":
                    user.setLeetcodeVerified(true);
                    break;
                case "codechef":
                    user.setCodechefVerified(true);
                    break;
                case "codeforces":
                    user.setCodeforcesVerified(true);
                    break;
                case "gfg":
                case "geeksforgeeks":
                    user.setGfgVerified(true);
                    break;
            }

            // Award +100 EXP verification reward bonus
            user.setPoints(user.getPoints() + 100);
            user.updateRank();
            User saved = userRepository.save(user);

            response.put("success", true);
            response.put("message", "🎉 Ownership Verified! " + platformName + " handle @" + handle + " is now confirmed. +100 EXP awarded!");
            response.put("user", saved);
            return response;
        } else {
            response.put("success", false);
            response.put("message", "Verification code '" + token + "' was not found in your " + platformName + " bio. Please paste the code into your bio/about section, save it, and try again.");
            return response;
        }
    }

    private boolean checkLeetCodeBio(String handle, String token) {
        try {
            String graphqlQuery = "{\"query\":\"query getUserProfile($username: String!) { matchedUser(username: $username) { profile { aboutMe summary } } }\",\"variables\":{\"username\":\"" + handle + "\"}}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://leetcode.com/graphql"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .POST(HttpRequest.BodyPublishers.ofString(graphqlQuery))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body().contains(token);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean checkCodeforcesBio(String handle, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://codeforces.com/api/user.info?handles=" + handle))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body().contains(token);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean checkUrlContainsToken(String urlStr, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body().contains(token);
            }
        } catch (Exception ignored) {}
        return false;
    }
}
