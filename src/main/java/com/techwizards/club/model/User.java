package com.techwizards.club.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    private String email;
    private String password;
    private String role = "MEMBER"; // "ADMIN" or "MEMBER"
    private Integer points = 0;
    private Integer streak = 0;
    private LocalDate lastSolvedDate;
    private LocalDate joinDate = LocalDate.now();
    private String githubUsername;
    private String leetcodeUsername;
    private String rankName = "Novice";

    // Profile Details
    private String fullName = "";
    private String academicYear = "2nd Year";
    private String statusEmoji = "🤩";
    private String bio = "";
    private String avatarUrl = "";

    // Platform Handles
    private String codechefUsername = "";
    private String codeforcesUsername = "";
    private String gfgUsername = "";

    // Platform Activity Metrics & Stats
    private Integer leetcodeSolved = 0;
    private String leetcodeRank = "Unrated";
    private Integer leetcodeBadges = 0;

    private String codechefStars = "1 Star(s)";
    private Integer codechefRating = 0;
    private String codechefRank = "0";
    private Integer codechefSolved = 0;

    private Integer codeforcesRating = 0;
    private String codeforcesRank = "unrated";
    private Integer codeforcesSolved = 0;

    private Integer gfgRank = 0;
    private Integer gfgScore = 0;
    private Integer gfgSolved = 0;

    private Integer previousRank = 19;
    private Integer maxSolvedDay = 2;
    private Double avgSolvedDay = 0.29;

    // Platform Verification Status & Token
    private Boolean leetcodeVerified = false;
    private Boolean codechefVerified = false;
    private Boolean codeforcesVerified = false;
    private Boolean gfgVerified = false;
    private String verificationToken = "";

    // Membership Approval Status: "APPROVED", "PENDING", "REJECTED"
    private String approvalStatus = "APPROVED";

    public User() {
    }

    public User(String username, String email) {
        this.username = username;
        this.email = email;
        this.points = 0;
        this.streak = 0;
        this.joinDate = LocalDate.now();
        this.rankName = "Novice";
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(this.role);
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
        updateRank();
    }

    public Integer getStreak() {
        return streak;
    }

    public void setStreak(Integer streak) {
        this.streak = streak;
    }

    public LocalDate getLastSolvedDate() {
        return lastSolvedDate;
    }

    public void setLastSolvedDate(LocalDate lastSolvedDate) {
        this.lastSolvedDate = lastSolvedDate;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getLeetcodeUsername() {
        return leetcodeUsername;
    }

    public void setLeetcodeUsername(String leetcodeUsername) {
        this.leetcodeUsername = leetcodeUsername;
    }

    public String getRankName() {
        return rankName;
    }

    public void setRankName(String rankName) {
        this.rankName = rankName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getStatusEmoji() {
        return statusEmoji;
    }

    public void setStatusEmoji(String statusEmoji) {
        this.statusEmoji = statusEmoji;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getCodechefUsername() {
        return codechefUsername;
    }

    public void setCodechefUsername(String codechefUsername) {
        this.codechefUsername = codechefUsername;
    }

    public String getCodeforcesUsername() {
        return codeforcesUsername;
    }

    public void setCodeforcesUsername(String codeforcesUsername) {
        this.codeforcesUsername = codeforcesUsername;
    }

    public String getGfgUsername() {
        return gfgUsername;
    }

    public void setGfgUsername(String gfgUsername) {
        this.gfgUsername = gfgUsername;
    }

    public Integer getLeetcodeSolved() {
        return leetcodeSolved;
    }

    public void setLeetcodeSolved(Integer leetcodeSolved) {
        this.leetcodeSolved = leetcodeSolved;
    }

    public String getLeetcodeRank() {
        return leetcodeRank;
    }

    public void setLeetcodeRank(String leetcodeRank) {
        this.leetcodeRank = leetcodeRank;
    }

    public Integer getLeetcodeBadges() {
        return leetcodeBadges;
    }

    public void setLeetcodeBadges(Integer leetcodeBadges) {
        this.leetcodeBadges = leetcodeBadges;
    }

    public String getCodechefStars() {
        return codechefStars;
    }

    public void setCodechefStars(String codechefStars) {
        this.codechefStars = codechefStars;
    }

    public Integer getCodechefRating() {
        return codechefRating;
    }

    public void setCodechefRating(Integer codechefRating) {
        this.codechefRating = codechefRating;
    }

    public String getCodechefRank() {
        return codechefRank;
    }

    public void setCodechefRank(String codechefRank) {
        this.codechefRank = codechefRank;
    }

    public Integer getCodechefSolved() {
        return codechefSolved;
    }

    public void setCodechefSolved(Integer codechefSolved) {
        this.codechefSolved = codechefSolved;
    }

    public Integer getCodeforcesRating() {
        return codeforcesRating;
    }

    public void setCodeforcesRating(Integer codeforcesRating) {
        this.codeforcesRating = codeforcesRating;
    }

    public String getCodeforcesRank() {
        return codeforcesRank;
    }

    public void setCodeforcesRank(String codeforcesRank) {
        this.codeforcesRank = codeforcesRank;
    }

    public Integer getCodeforcesSolved() {
        return codeforcesSolved;
    }

    public void setCodeforcesSolved(Integer codeforcesSolved) {
        this.codeforcesSolved = codeforcesSolved;
    }

    public Integer getGfgRank() {
        return gfgRank;
    }

    public void setGfgRank(Integer gfgRank) {
        this.gfgRank = gfgRank;
    }

    public Integer getGfgScore() {
        return gfgScore;
    }

    public void setGfgScore(Integer gfgScore) {
        this.gfgScore = gfgScore;
    }

    public Integer getGfgSolved() {
        return gfgSolved;
    }

    public void setGfgSolved(Integer gfgSolved) {
        this.gfgSolved = gfgSolved;
    }

    public Integer getPreviousRank() {
        return previousRank;
    }

    public void setPreviousRank(Integer previousRank) {
        this.previousRank = previousRank;
    }

    public Integer getMaxSolvedDay() {
        return maxSolvedDay;
    }

    public void setMaxSolvedDay(Integer maxSolvedDay) {
        this.maxSolvedDay = maxSolvedDay;
    }

    public Double getAvgSolvedDay() {
        return avgSolvedDay;
    }

    public void setAvgSolvedDay(Double avgSolvedDay) {
        this.avgSolvedDay = avgSolvedDay;
    }

    public Boolean getLeetcodeVerified() {
        return leetcodeVerified != null && leetcodeVerified;
    }

    public void setLeetcodeVerified(Boolean leetcodeVerified) {
        this.leetcodeVerified = leetcodeVerified;
    }

    public Boolean getCodechefVerified() {
        return codechefVerified != null && codechefVerified;
    }

    public void setCodechefVerified(Boolean codechefVerified) {
        this.codechefVerified = codechefVerified;
    }

    public Boolean getCodeforcesVerified() {
        return codeforcesVerified != null && codeforcesVerified;
    }

    public void setCodeforcesVerified(Boolean codeforcesVerified) {
        this.codeforcesVerified = codeforcesVerified;
    }

    public Boolean getGfgVerified() {
        return gfgVerified != null && gfgVerified;
    }

    public void setGfgVerified(Boolean gfgVerified) {
        this.gfgVerified = gfgVerified;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getApprovalStatus() {
        return approvalStatus != null ? approvalStatus : "APPROVED";
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    // Helper method to automatically calculate and update user rank based on points
    public void updateRank() {
        if (this.points == null) this.points = 0;
        if (this.points >= 1000) {
            this.rankName = "Grandmaster";
        } else if (this.points >= 500) {
            this.rankName = "Code Wizard";
        } else if (this.points >= 250) {
            this.rankName = "Specialist";
        } else if (this.points >= 100) {
            this.rankName = "Apprentice";
        } else {
            this.rankName = "Novice";
        }
    }
}
