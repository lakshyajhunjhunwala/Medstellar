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
                .connectTimeout(Duration.ofSeconds(10))
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
                    verified = checkLeetCodeProfile(handle, token);
                    platformName = "LeetCode";
                } else if ("codeforces".equals(p)) {
                    verified = checkCodeforcesProfile(handle, token);
                    platformName = "Codeforces";
                } else if ("codechef".equals(p)) {
                    verified = checkCodeChefProfile(handle, token);
                    platformName = "CodeChef";
                } else if ("gfg".equals(p) || "geeksforgeeks".equals(p)) {
                    verified = checkGfgProfile(handle, token);
                    platformName = "GeeksforGeeks";
                }
            } catch (Exception e) {
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

            // EXP Calculation Rules:
            // 1. GFG and LeetCode: Flat 100 EXP regardless of question count
            // 2. CodeChef: For every question solved in CodeChef contests, 100 EXP each
            int expAward = 100;
            String expMsg = "+100 EXP awarded!";
            if ("codechef".equals(p)) {
                int solvedCount = (user.getCodechefSolved() != null && user.getCodechefSolved() > 0) ? user.getCodechefSolved() : 1;
                expAward = solvedCount * 100;
                expMsg = "+" + expAward + " EXP awarded (" + solvedCount + " contest question(s) x 100 EXP)!";
            } else if ("leetcode".equals(p) || "gfg".equals(p) || "geeksforgeeks".equals(p)) {
                expAward = 100;
                expMsg = "+100 EXP awarded (flat platform reward)!";
            }

            user.setPoints(user.getPoints() + expAward);
            user.updateRank();
            User saved = userRepository.save(user);

            response.put("success", true);
            response.put("message", "🎉 Ownership Verified! " + platformName + " handle @" + handle + " is now confirmed. " + expMsg);
            response.put("user", saved);
            return response;
        } else {
            response.put("success", false);
            response.put("message", "Verification token '" + token + "' was not found in your " + platformName + " Profile Name (or Bio/Organization). Please set your " + platformName + " Profile Name to '" + token + "', save it, and try again.");
            return response;
        }
    }

    private boolean checkLeetCodeProfile(String handle, String token) {
        try {
            // Check LeetCode public GraphQL user profile (realName, aboutMe, summary)
            String graphqlQuery = "{\"query\":\"query getUserProfile($username: String!) { matchedUser(username: $username) { profile { realName aboutMe summary } } }\",\"variables\":{\"username\":\"" + handle + "\"}}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://leetcode.com/graphql"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://leetcode.com/" + handle + "/")
                    .POST(HttpRequest.BodyPublishers.ofString(graphqlQuery))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                if (resp.body().toLowerCase().contains(token.toLowerCase())) {
                    return true;
                }
            }

            // Fallback: check public mirrors
            HttpRequest fallbackReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://alfa-leetcode-api.onrender.com/userProfile/" + handle))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> fallbackResp = httpClient.send(fallbackReq, HttpResponse.BodyHandlers.ofString());
            if (fallbackResp.statusCode() == 200 && fallbackResp.body() != null) {
                return fallbackResp.body().toLowerCase().contains(token.toLowerCase());
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean checkCodeforcesProfile(String handle, String token) {
        try {
            // Codeforces official user.info API returns firstName, lastName, organization
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://codeforces.com/api/user.info?handles=" + handle))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body().toLowerCase().contains(token.toLowerCase());
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean checkCodeChefProfile(String handle, String token) {
        try {
            // Check CodeChef profile or CodeChef API mirror for display name / bio
            String[] urls = {
                "https://codechef-api.vercel.app/handle/" + handle,
                "https://www.codechef.com/users/" + handle
            };
            for (String url : urls) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(6))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .GET()
                            .build();

                    HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 && resp.body() != null && resp.body().toLowerCase().contains(token.toLowerCase())) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean checkGfgProfile(String handle, String token) {
        try {
            // Check GeeksforGeeks practice profile or auth profile for name/bio
            String[] urls = {
                "https://auth.geeksforgeeks.org/user/" + handle + "/",
                "https://www.geeksforgeeks.org/user/" + handle + "/",
                "https://practiceapi.geeksforgeeks.org/api/v1/user/problems/submissions/" + handle + "/"
            };
            for (String url : urls) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(6))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .GET()
                            .build();

                    HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 && resp.body() != null && resp.body().toLowerCase().contains(token.toLowerCase())) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }
}
