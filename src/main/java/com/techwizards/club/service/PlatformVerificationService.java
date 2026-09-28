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
                System.err.println("Verification network check error for " + platform + ": " + e.getMessage());
                response.put("success", false);
                response.put("message", "Could not reach " + platform + " servers (" + e.getMessage() + "). You can use Instant Verify for instant confirmation.");
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

            // Save verified state (EXP is credited at the end of the day during daily platform sync)
            User saved = userRepository.save(user);

            response.put("success", true);
            response.put("message", "🎉 Ownership Verified! " + platformName + " handle @" + handle + " is now confirmed. Verified platform EXP will be audited and credited at the end of the day according to club rules!");
            response.put("user", saved);
            return response;
        } else {
            response.put("success", false);
            response.put("message", "Verification token '" + token + "' was not detected in @" + handle + " on " + platformName + ". Make sure you added '" + token + "' to your Name or Bio/Summary and saved it. If you recently saved, external caches can take 1-2 mins to refresh, or you can use Instant Verify.");
            return response;
        }
    }

    private boolean checkLeetCodeProfile(String handle, String token) {
        try {
            // LeetCode GraphQL User Profile: query realName and aboutMe (DO NOT include 'summary' as it is invalid schema)
            String cleanHandle = handle.trim();
            String graphqlQuery = "{\"query\":\"query getUserProfile($username: String!) { matchedUser(username: $username) { profile { realName aboutMe } } }\",\"variables\":{\"username\":\"" + cleanHandle + "\"}}";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://leetcode.com/graphql"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://leetcode.com/" + cleanHandle + "/")
                    .POST(HttpRequest.BodyPublishers.ofString(graphqlQuery))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("LeetCode GraphQL check for @" + cleanHandle + ": status " + resp.statusCode());
            if (resp.statusCode() == 200 && resp.body() != null) {
                String body = resp.body().toLowerCase();
                String target = token.toLowerCase();
                if (body.contains(target)) {
                    System.out.println("Token " + token + " found in LeetCode response for @" + cleanHandle);
                    return true;
                }
            }

            // Fallback: check public mirrors
            try {
                HttpRequest fallbackReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://alfa-leetcode-api.onrender.com/userProfile/" + cleanHandle))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();
                HttpResponse<String> fallbackResp = httpClient.send(fallbackReq, HttpResponse.BodyHandlers.ofString());
                if (fallbackResp.statusCode() == 200 && fallbackResp.body() != null) {
                    if (fallbackResp.body().toLowerCase().contains(token.toLowerCase())) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            System.err.println("LeetCode check error: " + e.getMessage());
        }
        return false;
    }

    private boolean checkCodeforcesProfile(String handle, String token) {
        try {
            String cleanHandle = handle.trim();
            // Codeforces official user.info API returns firstName, lastName, organization
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://codeforces.com/api/user.info?handles=" + cleanHandle))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Codeforces API check for @" + cleanHandle + ": status " + resp.statusCode());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body().toLowerCase().contains(token.toLowerCase());
            }
        } catch (Exception e) {
            System.err.println("Codeforces check error: " + e.getMessage());
        }
        return false;
    }

    private boolean checkCodeChefProfile(String handle, String token) {
        try {
            String cleanHandle = handle.trim();
            // Directly query CodeChef profile page
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.codechef.com/users/" + cleanHandle))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("CodeChef check for @" + cleanHandle + ": status " + resp.statusCode());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body().toLowerCase().contains(token.toLowerCase());
            }
        } catch (Exception e) {
            System.err.println("CodeChef check error: " + e.getMessage());
        }
        return false;
    }

    private boolean checkGfgProfile(String handle, String token) {
        try {
            String cleanHandle = handle.trim();
            String[] urls = {
                "https://www.geeksforgeeks.org/user/" + cleanHandle + "/",
                "https://www.geeksforgeeks.org/user/" + cleanHandle + "/profile",
                "https://auth.geeksforgeeks.org/user/" + cleanHandle + "/"
            };
            for (String url : urls) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(6))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .GET()
                            .build();

                    HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("GFG check (" + url + "): status " + resp.statusCode());
                    if (resp.statusCode() == 200 && resp.body() != null && resp.body().toLowerCase().contains(token.toLowerCase())) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("GFG check error: " + e.getMessage());
        }
        return false;
    }
}
