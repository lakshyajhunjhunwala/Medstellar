package com.techwizards.club.config;

import com.techwizards.club.model.Announcement;
import com.techwizards.club.model.Project;
import com.techwizards.club.model.Quiz;
import com.techwizards.club.model.SecurityLog;
import com.techwizards.club.model.User;
import com.techwizards.club.repository.AnnouncementRepository;
import com.techwizards.club.repository.ProjectRepository;
import com.techwizards.club.repository.QuizRepository;
import com.techwizards.club.repository.SecurityLogRepository;
import com.techwizards.club.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final ProjectRepository projectRepository;
    private final SecurityLogRepository securityLogRepository;
    private final AnnouncementRepository announcementRepository;

    public DatabaseSeeder(UserRepository userRepository,
                          QuizRepository quizRepository,
                          ProjectRepository projectRepository,
                          SecurityLogRepository securityLogRepository,
                          AnnouncementRepository announcementRepository) {
        this.userRepository = userRepository;
        this.quizRepository = quizRepository;
        this.projectRepository = projectRepository;
        this.securityLogRepository = securityLogRepository;
        this.announcementRepository = announcementRepository;
    }

    @Override
    public void run(String... args) {
        try {
            seedQuizzes();
            seedProjects();
            seedSecurityLogs();
            seedAnnouncements();
            seedDefaultUsers();
        } catch (Exception e) {
            System.err.println("Warning: Database seeding encountered an issue: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void seedQuizzes() {
        if (quizRepository.count() > 0) return;

        List<Quiz> quizzes = Arrays.asList(
            // JavaScript
            new Quiz("JavaScript", "Which keyword is used to declare a block-scoped variable in JavaScript?", "var", "let", "define", "make", "B", 15),
            new Quiz("JavaScript", "What is the output of 'typeof null' in JavaScript?", "\"null\"", "\"undefined\"", "\"object\"", "\"number\"", "C", 20),
            new Quiz("JavaScript", "Which method adds one or more elements to the end of an array and returns its new length?", "pop()", "push()", "shift()", "unshift()", "B", 10),
            new Quiz("JavaScript", "Which operator is used for strict equality comparison (checks both value and type)?", "==", "=", "===", "equals", "C", 15),

            // Python
            new Quiz("Python", "How do you start a comment in Python?", "//", "/*", "#", "--", "C", 10),
            new Quiz("Python", "Which data structure in Python is mutable?", "tuple", "list", "string", "frozenset", "B", 15),
            new Quiz("Python", "What is the output of len(['a', 'b', 'c']) in Python?", "0", "1", "2", "3", "D", 10),
            new Quiz("Python", "What keyword is used to define functions in Python?", "function", "def", "func", "define", "B", 15),

            // Java
            new Quiz("Java", "Which access modifier makes a member visible only within its own class?", "public", "protected", "private", "default (package)", "C", 15),
            new Quiz("Java", "What is the superclass of all classes in Java?", "Class", "Object", "String", "System", "B", 10),
            new Quiz("Java", "Which interface does NOT allow duplicate elements?", "List", "Set", "Map", "Collection", "B", 15),
            new Quiz("Java", "Which keyword is used to inherit a class in Java?", "implements", "extends", "inherits", "super", "B", 10),

            // C++
            new Quiz("C++", "Which operator is used to deallocate memory created dynamically in C++?", "free", "delete", "remove", "destruct", "B", 20),
            new Quiz("C++", "What is the return type of the main() function in C++ standard?", "void", "int", "char", "float", "B", 10),
            new Quiz("C++", "Which symbol is used to include headers in C++?", "@", "#", "$", "%", "B", 10),
            new Quiz("C++", "What is the default access specifier for members of a class in C++?", "public", "private", "protected", "friend", "B", 15)
        );

        quizRepository.saveAll(quizzes);
        System.out.println("Seeded " + quizzes.size() + " quizzes successfully.");
    }

    private void seedProjects() {
        if (projectRepository.count() > 0) {
            projectRepository.findAll().forEach(p -> {
                boolean changed = false;
                if ("TechWizards Official Portal".equals(p.getName())) {
                    p.setName("MEDSTELLAR Official Portal");
                    p.setDescription("The central portal and landing platform for MEDSTELLAR Technical Club. Built with Spring Boot, HTML, CSS, JS, and MySQL.");
                    p.setGithubLink("https://github.com/medstellar/portal");
                    changed = true;
                }
                if (p.getGithubLink() != null && p.getGithubLink().contains("github.com/techwizards/")) {
                    p.setGithubLink(p.getGithubLink().replace("github.com/techwizards/", "github.com/medstellar/"));
                    changed = true;
                }
                if (changed) {
                    projectRepository.save(p);
                }
            });
            return;
        }

        List<Project> projects = Arrays.asList(
            new Project("MEDSTELLAR Official Portal", "The central portal and landing platform for MEDSTELLAR Technical Club. Built with Spring Boot, HTML, CSS, JS, and MySQL.", "Aarav Sharma", 5, "https://github.com/medstellar/portal", "Active"),
            new Project("Algorithmic Arena", "A local competitive programming tournament dashboard to host mini contests and track live standings on campus.", "Sneha Reddy", 3, "https://github.com/medstellar/arena", "Planning"),
            new Project("Campus Maps & Navigation", "Interactive, offline navigation application helping newcomers explore corridors, classrooms, and libraries.", "Vikram Sen", 4, "https://github.com/medstellar/nav-app", "Active"),
            new Project("Badge Attendance Scanner", "Face-recognition and QR badge verification utility for recording lecture and seminar attendance.", "Pooja Roy", 2, "https://github.com/medstellar/attendance", "Completed")
        );

        projectRepository.saveAll(projects);
        System.out.println("Seeded mock projects successfully.");
    }

    private void seedSecurityLogs() {
        if (securityLogRepository.count() > 0) return;

        List<SecurityLog> logs = Arrays.asList(
            new SecurityLog("Platform", "SUCCESS", "SSL Certificate successfully checked. TLS v1.3 handshake enforced.", "OK"),
            new SecurityLog("Access Control", "SUCCESS", "Role access checks compiled. Dynamic validation middleware active.", "OK"),
            new SecurityLog("Application", "INFO", "Automated SQL-injection & XSS input scanner online.", "OK"),
            new SecurityLog("Network", "SUCCESS", "DDOS Mitigation Scrubbing Center connected successfully.", "OK"),
            new SecurityLog("Network", "WARNING", "Blocked multiple brute force logins from IP 182.16.24.99.", "BLOCKED"),
            new SecurityLog("Monitoring", "INFO", "Database recovery point zip uploaded to secure S3 vault.", "OK"),
            new SecurityLog("Application", "SUCCESS", "Dependency vulnerability audit passed. 0 security threats found.", "OK")
        );

        securityLogRepository.saveAll(logs);
        System.out.println("Seeded security center logs successfully.");
    }

    private void seedAnnouncements() {
        if (announcementRepository.count() > 0) return;

        List<Announcement> announcements = Arrays.asList(
            new Announcement(
                "Club Platform Sync Protocol v2.4 Active",
                "Ensure your LeetCode, CodeChef, Codeforces and GeeksforGeeks handles are linked on your profile. EXP sync runs automatically to update your leaderboard standings.",
                "PINNED",
                "Lakshya (Lead)"
            ),
            new Announcement(
                "September 2026 Monthly Coding Sprint",
                "Complete at least 1 problem daily across any linked platform to maintain your streak shield and unlock the Century Coder badge.",
                "EVENT",
                "MEDSTELLAR Team"
            ),
            new Announcement(
                "Security Center Access Restricted",
                "Administrative & audit logs are strictly restricted to verified club leads via email authentication.",
                "SECURITY",
                "System Admin"
            )
        );

        announcementRepository.saveAll(announcements);
        System.out.println("Seeded club announcements successfully.");
    }

    private void seedDefaultUsers() {
        // 1. Ensure Lakshya (Administrator) exists and has ADMIN role
        userRepository.findByUsername("Lakshya").ifPresentOrElse(u -> {
            u.setRole("ADMIN");
            u.setPassword("Lakshya@98");
            u.setEmail("lakshya@college.edu");
            u.setFullName("Lakshya Jhunjhunwala");
            u.setApprovalStatus("APPROVED");
            userRepository.save(u);
        }, () -> {
            User user4 = new User("Lakshya", "lakshya@college.edu");
            user4.setPassword("Lakshya@98");
            user4.setRole("ADMIN");
            user4.setFullName("Lakshya Jhunjhunwala");
            user4.setAcademicYear("2nd Year");
            user4.setStatusEmoji("🤩");
            user4.setBio("Core Member & Competitive Programmer @ MEDSTELLAR. Passionate about DSA, Web Systems, and Open Source.");
            user4.setAvatarUrl("https://api.dicebear.com/7.x/bottts/svg?seed=Lakshya");
            user4.setPoints(1382);
            user4.setStreak(3);
            user4.setGithubUsername("lakshya-codes");
            user4.setLeetcodeUsername("lakshyajhunjhunwala");
            user4.setLeetcodeSolved(120);
            user4.setLeetcodeRank("1418643");
            user4.setLeetcodeBadges(1);
            user4.setCodechefUsername("glee_mount_91");
            user4.setCodechefStars("1 Star(s)");
            user4.setCodechefRank("11961455");
            user4.setCodechefRating(1196);
            user4.setCodechefSolved(34);
            user4.setCodeforcesUsername("lakshya9830");
            user4.setCodeforcesRating(0);
            user4.setCodeforcesRank("unrated");
            user4.setCodeforcesSolved(0);
            user4.setGfgUsername("lakshyajhun3plt");
            user4.setGfgRank(37);
            user4.setGfgScore(66);
            user4.setGfgSolved(47);
            user4.setPreviousRank(19);
            user4.setMaxSolvedDay(2);
            user4.setAvgSolvedDay(0.29);
            user4.setRankName("Grandmaster");
            user4.setLeetcodeVerified(true);
            user4.setCodechefVerified(true);
            user4.setCodeforcesVerified(true);
            user4.setGfgVerified(true);
            user4.setVerificationToken("MS-9830");
            user4.setApprovalStatus("APPROVED");
            user4.setJoinDate(LocalDate.now().minusDays(5));
            user4.setLastSolvedDate(LocalDate.now());
            userRepository.save(user4);
        });

        // 2. Ensure Shamirul exists
        if (userRepository.findByUsername("Shamirul").isEmpty()) {
            User user1 = new User("Shamirul", "shamirul@college.edu");
            user1.setPassword("member123");
            user1.setRole("MEMBER");
            user1.setFullName("Shamirul Huda");
            user1.setPoints(1150);
            user1.setStreak(12);
            user1.setGithubUsername("shamirul-huda");
            user1.setLeetcodeUsername("shamirul_lc");
            user1.setRankName("Grandmaster");
            user1.setApprovalStatus("APPROVED");
            user1.setJoinDate(LocalDate.now().minusDays(30));
            user1.setLastSolvedDate(LocalDate.now());
            userRepository.save(user1);
        }

        // 3. Ensure Saksham exists
        if (userRepository.findByUsername("Saksham").isEmpty()) {
            User user2 = new User("Saksham", "saksham@college.edu");
            user2.setPassword("member123");
            user2.setRole("MEMBER");
            user2.setFullName("Saksham Gupta");
            user2.setPoints(690);
            user2.setStreak(8);
            user2.setGithubUsername("saksham-gupta");
            user2.setLeetcodeUsername("saksham_lc");
            user2.setRankName("Code Wizard");
            user2.setApprovalStatus("APPROVED");
            user2.setJoinDate(LocalDate.now().minusDays(20));
            user2.setLastSolvedDate(LocalDate.now());
            userRepository.save(user2);
        }

        // 4. Ensure Revan exists
        if (userRepository.findByUsername("Revan").isEmpty()) {
            User user3 = new User("Revan", "revan@college.edu");
            user3.setPassword("member123");
            user3.setRole("MEMBER");
            user3.setFullName("V.R. Revan");
            user3.setPoints(670);
            user3.setStreak(5);
            user3.setGithubUsername("vr-revan");
            user3.setLeetcodeUsername("revan_lc");
            user3.setRankName("Code Wizard");
            user3.setApprovalStatus("APPROVED");
            user3.setJoinDate(LocalDate.now().minusDays(15));
            user3.setLastSolvedDate(LocalDate.now().minusDays(1));
            userRepository.save(user3);
        }

        // 5. Ensure any other existing users have proper defaults
        userRepository.findAll().forEach(u -> {
            boolean changed = false;
            if (u.getRole() == null || u.getRole().trim().isEmpty()) {
                u.setRole("MEMBER");
                changed = true;
            }
            if (u.getPassword() == null || u.getPassword().trim().isEmpty()) {
                u.setPassword("member123");
                changed = true;
            }
            if (u.getApprovalStatus() == null || u.getApprovalStatus().trim().isEmpty()) {
                u.setApprovalStatus("APPROVED");
                changed = true;
            }
            if (changed) {
                userRepository.save(u);
            }
        });
        System.out.println("Verified club users seeded successfully.");
    }
}
