# MEDSTELLAR Technical Club Portal

A premium, full-stack college technical club website containing:
1. **Landing Page**: Modern hero section with details about MEDSTELLAR, active statistics, and bios for the Founders and Tech Leads.
2. **Student Dashboard**: Dynamic greeting, daily streak counter (with warning notices for inactive streaks), rank badge calculated from user XP, and GitHub/LeetCode syncing simulations.
3. **Coding Quizzes**: Interactive quiz selector for JavaScript, Python, Java, and C++, with dynamic response checks, XP rewards, and countdown timers.
4. **Collaboration Board**: A project listing tracker where members can browse open-source club projects, apply to join teams, or propose new applications.
5. **Learning Hub**: Access to design asset links (Canva, Figma, Tailwind CSS docs) and custom YouTube video manuals.
6. **Security Operations Center**: Live security layers monitoring platform (Firewall, access control RBAC rules, SQL-injection shield, DDoS mitigation scrubbing, dynamic RAM/CPU load charts, and manual system backup/restore actions). Includes an automated security log console connected to database changes.

## Tech Stack
* **Frontend**: HTML5, CSS3 (variables, glassmorphism, keyframe animations), and JavaScript (ES6, dynamic fetches).
* **Backend**: Spring Boot 3.4.2 (Java 21), Spring Data JPA.
* **Database**: MySQL.

---

## Running Locally

### 1. Database Setup
Ensure you have a MySQL server running locally. Create the database schemas or let Spring Boot auto-create it:
* The database URL is configured to: `jdbc:mysql://localhost:3306/techwizards_db`
* The database name `techwizards_db` will be created automatically if it doesn't exist, thanks to the URL parameter `createDatabaseIfNotExist=true`.
* Open [application.properties](src/main/resources/application.properties) and update the credentials:
  ```properties
  spring.datasource.username=YOUR_MYSQL_USERNAME
  spring.datasource.password=YOUR_MYSQL_PASSWORD
  ```

### 2. Launch the Application
You can run this project in two ways:

#### Option A: Using Maven (Command Line)
If you have Maven installed, execute the following commands in the project root:
```bash
# Verify connection and compile
mvn clean compile

# Run the Spring Boot application
mvn spring-boot:run
```

#### Option B: Using an IDE (Recommended)
1. Import the root directory as a Maven project in **IntelliJ IDEA**, **Eclipse**, or **VS Code** (with the *Extension Pack for Java*).
2. The IDE will automatically resolve the POM dependencies.
3. Run the main class: `com.techwizards.club.TechWizardsApplication.java`.

### 3. Open the Website
Once the application starts successfully, open your browser and navigate to:
* **[http://localhost:8081](http://localhost:8081)**

The application automatically seeds the MySQL database on startup with default quizzes, mock projects, security events, and top performers.
