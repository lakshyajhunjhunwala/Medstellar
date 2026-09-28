package com.techwizards.club.service;

import com.techwizards.club.model.User;
import com.techwizards.club.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Checks CodeChef recent submissions for problems solved in a contest on a particular day.
     * Only counts unique accepted problems belonging to a contest on that date.
     */
    public int getCodechefContestSolvedOnDate(String handle, LocalDate date) {
        if (handle == null || handle.trim().isEmpty()) return 0;
        try {
            String cleanHandle = handle.trim();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.codechef.com/recent/user?page=0&user_handle=" + cleanHandle))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) return 0;

            String body = resp.body();
            Pattern trPattern = Pattern.compile("<tr\\s*>(.*?)</tr>", Pattern.DOTALL);
            Matcher trMatcher = trPattern.matcher(body);

            Set<String> uniqueContestProblems = new HashSet<>();
            String dateFormatted = date.format(DateTimeFormatter.ofPattern("dd/MM/yy"));

            Pattern timePattern = Pattern.compile("title='([^']+)'");
            Pattern hrefPattern = Pattern.compile("href='([^']+)'");
            Pattern statusPattern = Pattern.compile("title='([^']+)'style=");
            Pattern scorePattern = Pattern.compile("\\(([0-9]+)\\)");
            Pattern contestUrlPattern = Pattern.compile("^/([^/]+)/problems/([^/]+)$");

            while (trMatcher.find()) {
                String row = trMatcher.group(1);

                Matcher mTime = timePattern.matcher(row);
                Matcher mHref = hrefPattern.matcher(row);
                if (!mTime.find() || !mHref.find()) continue;

                String timeStr = mTime.group(1).trim();
                String hrefStr = mHref.group(1).trim().replace("\\/", "/");

                Matcher mStatus = statusPattern.matcher(row);
                String statusStr = mStatus.find() ? mStatus.group(1).trim().toLowerCase() : "";

                Matcher mScore = scorePattern.matcher(row);
                int scoreVal = mScore.find() ? Integer.parseInt(mScore.group(1)) : 0;

                boolean isAccepted = (scoreVal == 100) || (statusStr.contains("accepted") && !statusStr.contains("partially"));
                if (!isAccepted) continue;

                Matcher mContest = contestUrlPattern.matcher(hrefStr);
                if (!mContest.find()) continue;

                String contestCode = mContest.group(1).toUpperCase();
                String problemCode = mContest.group(2).toUpperCase();
                if ("PROBLEMS".equals(contestCode) || "PRACTICE".equals(contestCode) || "SUBMIT".equals(contestCode)) {
                    continue;
                }

                boolean isToday = false;
                if (timeStr.contains("sec ago") || timeStr.contains("min ago")) {
                    isToday = true;
                } else if (timeStr.contains("hour ago") || timeStr.contains("hours ago")) {
                    try {
                        Matcher digitMatcher = Pattern.compile("(\\d+)").matcher(timeStr);
                        if (digitMatcher.find() && Integer.parseInt(digitMatcher.group(1)) < 24) {
                            isToday = true;
                        }
                    } catch (Exception ignored) {
                        isToday = true;
                    }
                } else if (timeStr.contains(dateFormatted)) {
                    isToday = true;
                }

                if (isToday) {
                    uniqueContestProblems.add(contestCode + ":" + problemCode);
                }
            }

            return uniqueContestProblems.size();
        } catch (Exception e) {
            System.err.println("Error fetching CodeChef daily contest activity for @" + handle + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Checks if user has solved any questions on LeetCode on a particular date.
     */
    public boolean hasLeetcodeSolvedOnDate(String handle, LocalDate date) {
        if (handle == null || handle.trim().isEmpty()) return false;
        try {
            String cleanHandle = handle.trim();
            String graphqlQuery = "{\"query\":\"query recentSubmissions($username: String!) { recentSubmissionList(username: $username, limit: 15) { title timestamp statusDisplay } }\",\"variables\":{\"username\":\"" + cleanHandle + "\"}}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://leetcode.com/graphql"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .POST(HttpRequest.BodyPublishers.ofString(graphqlQuery))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) return false;

            String body = resp.body();
            Pattern subPattern = Pattern.compile("\"timestamp\":\"?(\\d+)\"?.*?\"statusDisplay\":\"([^\"]+)\"");
            Matcher m = subPattern.matcher(body);

            ZoneId zone = ZoneId.of("Asia/Kolkata");
            while (m.find()) {
                long ts = Long.parseLong(m.group(1));
                String status = m.group(2);
                if ("Accepted".equalsIgnoreCase(status)) {
                    LocalDate subDate = Instant.ofEpochSecond(ts).atZone(zone).toLocalDate();
                    if (date.equals(subDate)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error checking LeetCode daily solved for @" + handle + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks Codeforces submissions solved on a particular date.
     */
    public int getCodeforcesSolvedOnDate(String handle, LocalDate date) {
        if (handle == null || handle.trim().isEmpty()) return 0;
        try {
            String cleanHandle = handle.trim();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://codeforces.com/api/user.status?handle=" + cleanHandle + "&from=1&count=20"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) return 0;

            String body = resp.body();
            Pattern subPattern = Pattern.compile("\"creationTimeSeconds\":(\\d+).*?\"verdict\":\"([^\"]+)\"");
            Matcher m = subPattern.matcher(body);

            ZoneId zone = ZoneId.of("Asia/Kolkata");
            int solvedCount = 0;
            while (m.find()) {
                long ts = Long.parseLong(m.group(1));
                String verdict = m.group(2);
                if ("OK".equalsIgnoreCase(verdict)) {
                    LocalDate subDate = Instant.ofEpochSecond(ts).atZone(zone).toLocalDate();
                    if (date.equals(subDate)) {
                        solvedCount++;
                    }
                }
            }
            return solvedCount;
        } catch (Exception e) {
            System.err.println("Error checking Codeforces daily solved for @" + handle + ": " + e.getMessage());
            return 0;
        }
    }
}
