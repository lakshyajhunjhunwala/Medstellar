// MEDSTELLAR Web Application Logic

// API Configuration (Supports Vercel proxy, custom domain, or local development)
const API_BASE = window.MEDSTELLAR_API_BASE || localStorage.getItem('medstellar_api_url') || '/api';

// Global Client State
let currentUser = null;
let currentQuizzes = [];
let currentQuestionIndex = 0;
let quizScore = 0;
let quizEarnedXP = 0;
let quizTimerInterval = null;
let securityStatusInterval = null;
let sessionChecking = false;

// Application Initialization
document.addEventListener('DOMContentLoaded', () => {
    // Initialize Lucide Icons
    if (window.lucide) {
        window.lucide.createIcons();
    }

    // Start Live Clock
    startClock();

    // Load Public Hero Podium
    loadPublicHeroPodium();

    // Check Login State & Setup Shells
    checkLoginSession();

    // Setup Router & Event Listeners
    setupNavigation();
    setupEventHandlers();
    
    // Default Route
    handleHashRoute();
});

// 1. Live Header Clock
function startClock() {
    const clock = document.getElementById('liveTime');
    if (!clock) return;
    
    setInterval(() => {
        const now = new Date();
        clock.textContent = now.toLocaleTimeString();
    }, 1000);
}

// 2. Authentication & Shell Toggle Manager
function checkLoginSession() {
    const savedUser = localStorage.getItem('medstellar_username') || localStorage.getItem('techwizards_username');
    if (savedUser) {
        sessionChecking = true;
        fetchUserProfile(savedUser);
    } else {
        updateUIForLoggedOutState();
        showPublicShell();
    }
}

function fetchUserProfile(username) {
    fetch(`${API_BASE}/users/${encodeURIComponent(username)}`)
        .then(response => {
            if (!response.ok) throw new Error("User not found");
            return response.json();
        })
        .then(user => {
            currentUser = user;
            updateUIForLoggedInState();
            showPortalShell();
            
            // Redirect to dashboard if landing page was accessed directly while logged in
            const hash = window.location.hash || '#home';
            if (hash === '#home' || hash === '') {
                window.location.hash = '#dashboard';
            }
            sessionChecking = false;
            handleHashRoute();
        })
        .catch(err => {
            console.error("Session restoration failed:", err);
            sessionChecking = false;
            logout();
        });
}

function showPublicShell() {
    document.getElementById('public-shell').classList.remove('hidden');
    document.getElementById('portal-shell').classList.add('hidden');
}

function showPortalShell() {
    document.getElementById('public-shell').classList.add('hidden');
    document.getElementById('portal-shell').classList.remove('hidden');
}

function loginUser(email, password) {
    const submitBtn = document.querySelector('#loginForm button[type="submit"]');
    const originalText = submitBtn ? submitBtn.innerHTML : 'Authenticate & Enter';
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = 'Connecting to server...';
    }

    const params = new URLSearchParams();
    params.append('email', email);
    params.append('password', password);

    fetch(`${API_BASE}/users/login?${params.toString()}`, {
        method: 'POST'
    })
    .then(async response => {
        if (!response.ok) {
            if (response.status === 429) {
                throw new Error("Render cloud server is waking up from hibernation. Please wait ~10 seconds and try again!");
            }
            if (response.status >= 500) {
                throw new Error("Server is currently warming up (Render free tier). Please wait a few seconds and try again.");
            }
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.message || (response.status === 401 ? "Incorrect password. Passwords are case-sensitive." : "Authentication failed! Check credentials."));
        }
        return response.json();
    })
    .then(user => {
        currentUser = user;
        localStorage.setItem('medstellar_username', user.username);
        updateUIForLoggedInState();
        
        // Hide login modal
        hideLoginModal();
        
        // Transition shells
        showPortalShell();
        
        // Redirect to Dashboard
        if (window.location.hash === '#dashboard') {
            handleHashRoute();
        } else {
            window.location.hash = '#dashboard';
        }
    })
    .catch(err => {
        alert(err.message || "Authentication failed! Check credentials.");
        console.error(err);
    })
    .finally(() => {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
        }
    });
}

function logout() {
    currentUser = null;
    localStorage.removeItem('medstellar_username');
    localStorage.removeItem('techwizards_username');
    updateUIForLoggedOutState();
    
    // Transition shells back to public
    showPublicShell();
    
    // Redirect to public landing
    window.location.hash = '#home';
}

function updateUIForLoggedInState() {
    if (!currentUser) return;

    // Show stats badges in top-bar of portal
    document.getElementById('headerStreakCount').textContent = currentUser.streak;
    document.getElementById('headerPointsCount').textContent = currentUser.points;

    // Sidebar profile footer view
    document.getElementById('sidebarUsername').textContent = currentUser.username;
    document.getElementById('sidebarUserRank').textContent = currentUser.rankName;
    document.getElementById('userAvatar').textContent = currentUser.username.charAt(0).toUpperCase();

    // Show Security Center in sidebar ONLY for Administrator (Lakshya)
    const navSecurityItem = document.getElementById('navSecurityItem');
    if (navSecurityItem) {
        const isAdmin = currentUser.role === 'ADMIN' ||
                        currentUser.admin === true ||
                        (currentUser.username && currentUser.username.toLowerCase() === 'lakshya') ||
                        (currentUser.email && currentUser.email.toLowerCase().includes('lakshya'));
        navSecurityItem.style.display = isAdmin ? 'flex' : 'none';
    }

    // Populate profile form handles if elements exist
    const ghInput = document.getElementById('githubInput');
    if (ghInput) ghInput.value = currentUser.githubUsername || '';
    const lcInput = document.getElementById('leetcodeInput');
    if (lcInput) lcInput.value = currentUser.leetcodeUsername || '';

    // Check if streak is at risk
    checkStreakWarning();
    
    if (window.lucide) window.lucide.createIcons();
}

function updateUIForLoggedOutState() {
    // Clear forms if elements exist
    const ghInput = document.getElementById('githubInput');
    if (ghInput) ghInput.value = '';
    const lcInput = document.getElementById('leetcodeInput');
    if (lcInput) lcInput.value = '';
    const warningAlert = document.getElementById('streakWarningAlert');
    if (warningAlert) warningAlert.classList.add('hidden');

    const navSecurityItem = document.getElementById('navSecurityItem');
    if (navSecurityItem) {
        navSecurityItem.style.display = 'none';
    }
}

function checkStreakWarning() {
    if (!currentUser) return;
    const warningAlert = document.getElementById('streakWarningAlert');
    
    // Warning logic: if streak is 0 or user has not solved anything today
    const todayStr = new Date().toISOString().split('T')[0];
    const lastSolvedStr = currentUser.lastSolvedDate;

    if (currentUser.streak === 0 || lastSolvedStr !== todayStr) {
        warningAlert.classList.remove('hidden');
    } else {
        warningAlert.classList.add('hidden');
    }
}

// Public section anchors that do not require authentication
const publicSections = ['#home', '#about-club', '#ecosystem', '#performers', '#vision', ''];

// 3. Router System
function setupNavigation() {
    // Handle nav items active classes
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            const target = item.getAttribute('data-target');
            
            // Gate check for URL traversal
            if (!currentUser) {
                e.preventDefault();
                showPublicShell();
                showLoginModal();
                return;
            }
            
            navItems.forEach(n => n.classList.remove('active'));
            item.classList.add('active');
        });
    });

    // Handle smooth scrolling for public toolbar and navbar links
    const publicAnchorLinks = document.querySelectorAll('.floating-toolbar-srm a, .navbar-links-srm a');
    publicAnchorLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            const href = link.getAttribute('href');
            if (href && href.startsWith('#')) {
                const target = document.querySelector(href);
                if (target) {
                    e.preventDefault();
                    target.scrollIntoView({ behavior: 'smooth' });
                    history.replaceState(null, '', href);
                }
            }
        });
    });

    // Hash change listener
    window.addEventListener('hashchange', handleHashRoute);
}

function handleHashRoute() {
    if (sessionChecking) return;
    const hash = window.location.hash || '#home';
    
    // If not logged in, enforce landing shell
    if (!currentUser) {
        showPublicShell();
        if (publicSections.includes(hash)) {
            if (hash && hash !== '#home') {
                const targetEl = document.querySelector(hash);
                if (targetEl) {
                    targetEl.scrollIntoView({ behavior: 'smooth' });
                }
            }
            return;
        }
        // User tried to access a protected portal route manually while logged out
        window.location.hash = '#home';
        showLoginModal();
        return;
    }

    // If logged in
    showPortalShell();
    
    // If user accesses home route, redirect directly to dashboard portal
    if (hash === '#home' || hash === '') {
        window.location.hash = '#dashboard';
        return;
    }

    const viewId = hash.substring(1);
    navigateTo(viewId);
}

function navigateTo(viewId) {
    // Restrict Security Center view to Administrator (Lakshya) only
    if (viewId === 'security') {
        const isAdmin = currentUser && (
            currentUser.role === 'ADMIN' ||
            currentUser.admin === true ||
            (currentUser.username && currentUser.username.toLowerCase() === 'lakshya') ||
            (currentUser.email && currentUser.email.toLowerCase().includes('lakshya'))
        );
        if (!isAdmin) {
            alert("Access Denied: Security Operations Center is strictly restricted to administrator accounts.");
            window.location.hash = '#dashboard';
            return;
        }
    }

    // Update Active Menu
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        if (item.getAttribute('data-target') === viewId) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });

    // Toggle View Sections
    const views = document.querySelectorAll('.page-view');
    views.forEach(view => {
        if (view.id === `view-${viewId}`) {
            view.classList.add('active');
        } else {
            view.classList.remove('active');
        }
    });

    // Update Header title
    const viewTitles = {
        'dashboard': 'Student Dashboard',
        'profile': 'Member Profile & Platform Activity',
        'leaderboard': 'Dynamic Leaderboards',
        'quizzes': 'Interactive Code Quizzes',
        'projects': 'Collaboration Project Board',
        'resources': 'MEDSTELLAR Learning Hub',
        'security': 'Security Operations Center'
    };
    document.getElementById('currentViewTitle').textContent = viewTitles[viewId] || 'Portal';

    // Clear background timers/intervals if leaving security center
    if (viewId !== 'security' && securityStatusInterval) {
        clearInterval(securityStatusInterval);
        securityStatusInterval = null;
    }

    // Trigger page-specific loads
    if (viewId === 'dashboard') {
        loadDashboardData();
    } else if (viewId === 'profile') {
        loadProfilePageData();
    } else if (viewId === 'leaderboard') {
        loadLeaderboardPageData();
    } else if (viewId === 'quizzes') {
        resetQuizScreen();
    } else if (viewId === 'projects') {
        loadProjectsList();
    } else if (viewId === 'security') {
        loadSecurityDashboard();
        // Start auto-refresh interval for security metrics (fluctuations)
        if (!securityStatusInterval) {
            securityStatusInterval = setInterval(refreshSecurityMetricsOnly, 4000);
        }
    }
}

// 4. Page loading controllers

// A. Dashboard Data
// A. Dashboard Data
function loadDashboardData() {
    if (!currentUser) return;

    // Refresh user stats from DB first
    fetch(`${API_BASE}/users/${encodeURIComponent(currentUser.username)}`)
    .then(res => res.json())
    .then(user => {
        currentUser = user;
        updateUIForLoggedInState();

        // Welcome greetings
        document.getElementById('dashWelcomeUsername').textContent = user.username;
        document.getElementById('dashStreakVal').textContent = user.streak;
        document.getElementById('dashRankBadge').textContent = user.rankName;

        // Progress bar XP
        const nextLevelXP = calculateNextLevelXP(user.points);
        if (document.getElementById('dashXPLabel')) {
            document.getElementById('dashXPLabel').textContent = `${user.points} XP`;
        }
        if (document.getElementById('dashXPLabelProgress')) {
            document.getElementById('dashXPLabelProgress').textContent = `${user.points} / ${nextLevelXP} XP`;
        }
        
        // Calculate progress percentage
        const progressPercent = Math.min(100, (user.points / nextLevelXP) * 100);
        const barFill = document.getElementById('dashProgressBarFill');
        if (barFill) barFill.style.width = `${progressPercent}%`;

        // Render Leaderboard & Activity Calendar
        loadLeaderboard();
        renderActivityCalendar();
    });
}

function calculateNextLevelXP(points) {
    if (points >= 1000) return 2000;
    if (points >= 500) return 1000;
    if (points >= 250) return 500;
    if (points >= 100) return 250;
    return 100;
}

function loadLeaderboard() {
    fetch(`${API_BASE}/users/leaderboard`)
    .then(res => res.json())
    .then(users => {
        populatePerformersMedalList(users);
    });
}

function loadPublicHeroPodium() {
    fetch(`${API_BASE}/users/leaderboard`)
    .then(res => res.json())
    .then(users => {
        const podiumOuter = document.querySelector('#performers .podium-container');
        if (!podiumOuter) return;
        podiumOuter.innerHTML = '';

        // Match the users seeded or fallbacks
        const silverUser = users[1] || { username: 'MD Shamirul', points: 300 };
        const goldUser = users[0] || { username: 'Saksham Gupta', points: 450 };
        const bronzeUser = users[2] || { username: 'VR REVAN', points: 250 };

        const silverHtml = `
            <div class="podium-card silver-card">
                <div class="podium-badge">Silver</div>
                <div class="podium-icon"><i data-lucide="award"></i></div>
                <div class="podium-avatar">
                    <span class="avatar-letter">${silverUser.username.charAt(0).toUpperCase()}</span>
                </div>
                <h4 class="performer-name">${silverUser.username}</h4>
                <div class="performer-score">
                    <span class="score-num">${silverUser.points} XP</span>
                    <span class="score-check"><i data-lucide="check-circle-2"></i></span>
                </div>
            </div>
        `;

        const goldHtml = `
            <div class="podium-card gold-card">
                <div class="podium-badge">Gold</div>
                <div class="podium-icon"><i data-lucide="crown"></i></div>
                <div class="podium-avatar">
                    <span class="avatar-letter">${goldUser.username.charAt(0).toUpperCase()}</span>
                </div>
                <h4 class="performer-name">${goldUser.username}</h4>
                <div class="performer-score">
                    <span class="score-num">${goldUser.points} XP</span>
                    <span class="score-check"><i data-lucide="check-circle-2"></i></span>
                </div>
            </div>
        `;

        const bronzeHtml = `
            <div class="podium-card bronze-card">
                <div class="podium-badge">Bronze</div>
                <div class="podium-icon"><i data-lucide="award"></i></div>
                <div class="podium-avatar">
                    <span class="avatar-letter">${bronzeUser.username.charAt(0).toUpperCase()}</span>
                </div>
                <h4 class="performer-name">${bronzeUser.username}</h4>
                <div class="performer-score">
                    <span class="score-num">${bronzeUser.points} XP</span>
                    <span class="score-check"><i data-lucide="check-circle-2"></i></span>
                </div>
            </div>
        `;

        podiumOuter.innerHTML = silverHtml + goldHtml + bronzeHtml;
        if (window.lucide) window.lucide.createIcons();
    })
    .catch(err => console.error("Error loading public hero podium:", err));
}

function populatePerformersMedalList(users) {
    const listContainer = document.getElementById('performersMedalList');
    if (!listContainer) return;
    listContainer.innerHTML = '';

    users.forEach((u, index) => {
        if (index >= 5) return;

        const row = document.createElement('div');
        row.className = 'performer-medal-row';
        if (currentUser && u.username === currentUser.username) {
            row.classList.add('active-user');
        }

        let medalHtml = '';
        if (index === 0) medalHtml = `<span class="medal-badge gold">1 G</span>`;
        else if (index === 1) medalHtml = `<span class="medal-badge silver">2 S</span>`;
        else if (index === 2) medalHtml = `<span class="medal-badge bronze">2 B</span>`;
        else medalHtml = `<span class="medal-badge member">1 M</span>`;

        row.innerHTML = `
            <div class="performer-info-box">
                <div class="avatar-circle">${u.username.charAt(0).toUpperCase()}</div>
                <div class="p-meta">
                    <span class="p-name">${u.username}</span>
                    <div style="font-size: 10px; color: var(--text-secondary); margin-top: 2px;">
                        ${u.points} XP &bull; ${u.streak} Day Streak
                    </div>
                </div>
            </div>
            <div class="performer-medals">
                ${medalHtml}
            </div>
        `;
        listContainer.appendChild(row);
    });
}

function loadLeaderboardPageData() {
    fetch(`${API_BASE}/users/leaderboard`)
    .then(res => res.json())
    .then(users => {
        const podiumCards = document.getElementById('leaderboardPodiumCards');
        if (podiumCards) {
            podiumCards.innerHTML = '';
            
            const silverUser = users[1] || { username: 'Advait', points: 300 };
            const goldUser = users[0] || { username: 'Ritika', points: 450 };
            const bronzeUser = users[2] || { username: 'Sharmada', points: 250 };
            
            const silverCard = `
                <div class="podium-performer-card rank-2 glass">
                    <span class="rank-badge-srm">2</span>
                    <div class="performer-avatar-large">${silverUser.username.charAt(0).toUpperCase()}</div>
                    <h3>${silverUser.username}</h3>
                    <span class="performer-role-srm">Silver Performer</span>
                    <div class="points-box">${silverUser.points} <span>XP</span></div>
                </div>
            `;
            
            const goldCard = `
                <div class="podium-performer-card rank-1 glass">
                    <span class="rank-badge-srm">1</span>
                    <div class="performer-avatar-large">${goldUser.username.charAt(0).toUpperCase()}</div>
                    <h3>${goldUser.username}</h3>
                    <span class="performer-role-srm">Chapter Lead</span>
                    <div class="points-box">${goldUser.points} <span>XP</span></div>
                </div>
            `;
            
            const bronzeCard = `
                <div class="podium-performer-card rank-3 glass">
                    <span class="rank-badge-srm">3</span>
                    <div class="performer-avatar-large">${bronzeUser.username.charAt(0).toUpperCase()}</div>
                    <h3>${bronzeUser.username}</h3>
                    <span class="performer-role-srm">Bronze Performer</span>
                    <div class="points-box">${bronzeUser.points} <span>XP</span></div>
                </div>
            `;
            
            podiumCards.innerHTML = silverCard + goldCard + bronzeCard;
        }

        const fullTableBody = document.getElementById('leaderboardFullList');
        if (fullTableBody) {
            fullTableBody.innerHTML = '';
            users.forEach((u, index) => {
                const tr = document.createElement('tr');
                if (currentUser && u.username === currentUser.username) {
                    tr.className = 'active-user';
                }
                
                let rankClass = 'rank-other';
                if (index === 0) rankClass = 'rank-1';
                else if (index === 1) rankClass = 'rank-2';
                else if (index === 2) rankClass = 'rank-3';
                
                tr.innerHTML = `
                    <td><span class="rank-circle-td ${rankClass}">${index + 1}</span></td>
                    <td class="font-bold">${u.username}</td>
                    <td>${u.points} XP</td>
                    <td><i data-lucide="flame" class="fire-icon" style="width: 14px; display:inline-block; vertical-align:middle; margin-right:4px;"></i>${u.streak} Days</td>
                `;
                fullTableBody.appendChild(tr);
            });
        }
        
        if (window.lucide) window.lucide.createIcons();
    })
    .catch(err => console.error("Error loading leaderboard page data:", err));
}

function renderActivityCalendar() {
    const grid = document.getElementById('calendarDaysGrid');
    if (!grid) return;
    grid.innerHTML = '';
    
    const startDayOffset = 3; // July 2026 starts on Wednesday
    const totalDays = 31;
    const todayNum = 26;
    const activeDays = [5, 8, 12, 15, 19, 22, 25, todayNum];
    
    for (let i = 0; i < startDayOffset; i++) {
        const emptyCell = document.createElement('div');
        emptyCell.className = 'cal-day empty';
        grid.appendChild(emptyCell);
    }
    
    for (let day = 1; day <= totalDays; day++) {
        const cell = document.createElement('div');
        cell.className = 'cal-day';
        cell.textContent = day;
        
        if (day === todayNum) {
            cell.classList.add('today');
        }
        if (activeDays.includes(day)) {
            cell.classList.add('active');
        }
        grid.appendChild(cell);
    }
}

// B. Quizzes Engine
function resetQuizScreen() {
    document.getElementById('quizCategorySelector').classList.remove('hidden');
    document.getElementById('activeQuizScreen').classList.add('hidden');
    document.getElementById('quizResultScreen').classList.add('hidden');
    
    // Clear countdowns
    if (quizTimerInterval) clearInterval(quizTimerInterval);
}

function startQuiz(language) {
    document.getElementById('quizCategorySelector').classList.add('hidden');
    document.getElementById('activeQuizScreen').classList.remove('hidden');
    document.getElementById('activeQuizLang').textContent = language;

    fetch(`${API_BASE}/quizzes?language=${encodeURIComponent(language)}`)
    .then(res => res.json())
    .then(quizzes => {
        if (quizzes.length === 0) {
            alert("No quizzes available for this category.");
            resetQuizScreen();
            return;
        }

        // Shuffle questions
        currentQuizzes = quizzes.sort(() => 0.5 - Math.random()).slice(0, 5);
        currentQuestionIndex = 0;
        quizScore = 0;
        quizEarnedXP = 0;
        
        loadQuestion();
    });
}

function loadQuestion() {
    if (currentQuestionIndex >= currentQuizzes.length) {
        showQuizResults();
        return;
    }

    const currentQuiz = currentQuizzes[currentQuestionIndex];
    document.getElementById('quizIndexVal').textContent = currentQuestionIndex + 1;
    document.getElementById('quizTotalVal').textContent = currentQuizzes.length;
    document.getElementById('quizQuestionText').textContent = currentQuiz.question;
    
    document.getElementById('optionAText').textContent = currentQuiz.optionA;
    document.getElementById('optionBText').textContent = currentQuiz.optionB;
    document.getElementById('optionCText').textContent = currentQuiz.optionC;
    document.getElementById('optionDText').textContent = currentQuiz.optionD;

    // Reset option button styles
    const optionBtns = document.querySelectorAll('.option-btn');
    optionBtns.forEach(btn => {
        btn.className = 'option-btn';
        btn.disabled = false;
    });

    // Start Timer
    startQuizTimer();
}

function startQuizTimer() {
    if (quizTimerInterval) clearInterval(quizTimerInterval);
    
    const timerFill = document.getElementById('quizTimerBar');
    let timeLeft = 15; // 15 seconds
    timerFill.style.width = '100%';
    
    quizTimerInterval = setInterval(() => {
        timeLeft -= 0.1;
        const percent = (timeLeft / 15) * 100;
        timerFill.style.width = `${percent}%`;

        if (timeLeft <= 0) {
            clearInterval(quizTimerInterval);
            handleQuizAnswerSubmit(null); // Timeout (incorrect)
        }
    }, 100);
}

function handleQuizAnswerSubmit(selectedOption) {
    clearInterval(quizTimerInterval);
    
    const currentQuiz = currentQuizzes[currentQuestionIndex];
    
    // Disable all options during validation
    const optionBtns = document.querySelectorAll('.option-btn');
    optionBtns.forEach(btn => btn.disabled = true);

    const params = new URLSearchParams();
    params.append('username', currentUser.username);
    params.append('answer', selectedOption || '');

    fetch(`${API_BASE}/quizzes/${currentQuiz.id}/submit`, {
        method: 'POST',
        body: params
    })
    .then(res => res.json())
    .then(result => {
        // Highlight correct/incorrect answers on UI
        optionBtns.forEach(btn => {
            const opt = btn.getAttribute('data-option');
            if (opt === result.correctAnswer) {
                btn.classList.add('correct');
            }
            if (selectedOption === opt && !result.correct) {
                btn.classList.add('incorrect');
            }
        });

        // Award calculations
        if (result.correct) {
            quizScore++;
            quizEarnedXP += result.pointsAwarded;
        }

        // Update local memory user stats
        currentUser.points = result.newPoints;
        currentUser.streak = result.newStreak;
        currentUser.rankName = result.newRank;
        updateUIForLoggedInState();

        // Wait 1.5 seconds, then load next question
        setTimeout(() => {
            currentQuestionIndex++;
            loadQuestion();
        }, 1500);
    })
    .catch(err => {
        console.error("Error submitting answer:", err);
        alert("Verification server error.");
        resetQuizScreen();
    });
}

// C. Collaboration Projects
function loadProjectsList() {
    fetch(`${API_BASE}/projects`)
    .then(res => res.json())
    .then(projects => {
        const grid = document.getElementById('projectsGrid');
        grid.innerHTML = '';

        projects.forEach(p => {
            const card = document.createElement('div');
            card.className = 'project-card glass';

            const statusClass = p.status.toLowerCase();
            
            card.innerHTML = `
                <div class="project-header">
                    <h4 class="project-title">${p.name}</h4>
                    <span class="proj-status-badge ${statusClass}">${p.status}</span>
                </div>
                <div class="project-body">
                    <p class="project-desc">${p.description}</p>
                    <div class="project-metadata">
                        <div class="metadata-row">
                            <span>Project Lead:</span>
                            <span class="font-bold">${p.leadName}</span>
                        </div>
                        <div class="metadata-row">
                            <span>Team Members:</span>
                            <span class="font-bold" id="projMembers-${p.id}">${p.memberCount} Engineers</span>
                        </div>
                        <div class="metadata-row">
                            <span>Repository:</span>
                            <a href="${p.githubLink}" target="_blank">${p.githubLink ? 'View on GitHub' : 'Not linked'}</a>
                        </div>
                    </div>
                    <button class="btn btn-secondary w-full" onclick="joinProject(${p.id})">
                        <i data-lucide="git-pull-request" style="width:16px;"></i> Join Project Team
                    </button>
                </div>
            `;
            grid.appendChild(card);
        });

        if (window.lucide) window.lucide.createIcons();
    });
}

window.joinProject = function(projectId) {
    if (!currentUser) {
        showLoginModal();
        return;
    }

    const params = new URLSearchParams();
    params.append('username', currentUser.username);

    fetch(`${API_BASE}/projects/${projectId}/join`, {
        method: 'POST',
        body: params
    })
    .then(res => {
        if (!res.ok) throw new Error("Could not join");
        return res.json();
    })
    .then(updatedProject => {
        // Update member count on UI
        const label = document.getElementById(`projMembers-${projectId}`);
        if (label) {
            label.textContent = `${updatedProject.memberCount} Engineers`;
        }
        alert(`Awesome! You have requested to join ${updatedProject.name}. Check your portal mail for integration details.`);
        loadProjectsList();
    })
    .catch(err => {
        console.error(err);
        alert("Error joining team project.");
    });
};

// D. Security Operations Center (SOC) Dashboard
function loadSecurityDashboard() {
    loadPendingMembers();
    refreshSecurityLogs();
    refreshSecurityMetricsOnly();
}

function refreshSecurityLogs() {
    fetch(`${API_BASE}/security/logs`)
    .then(res => res.json())
    .then(logs => {
        const consoleLogs = document.getElementById('securityConsoleLogs');
        consoleLogs.innerHTML = '';

        logs.forEach(l => {
            const time = new Date(l.timestamp).toLocaleTimeString();
            const tagClass = l.eventType.toLowerCase();
            
            const line = document.createElement('div');
            line.className = 'console-log-line';
            line.innerHTML = `
                <span class="timestamp">[${time}]</span>
                <span class="tag ${tagClass}">${l.layer}::${l.eventType}</span>
                <span class="desc">${l.description}</span>
            `;
            consoleLogs.appendChild(line);
        });
        
        // Scroll console to top
        consoleLogs.scrollTop = 0;
    });
}

function refreshSecurityMetricsOnly() {
    fetch(`${API_BASE}/security/status`)
    .then(res => res.json())
    .then(status => {
        // Update Platform Layer
        const plat = status.platform;
        document.getElementById('firewallText').textContent = plat.firewall;
        document.getElementById('firewallText').className = `metric-value font-bold ${plat.firewall === 'ACTIVE' ? 'green' : 'amber'}`;
        
        document.getElementById('appShieldText').textContent = plat.appProtectionShield;
        document.getElementById('appShieldText').className = `metric-value font-bold ${plat.appProtectionShield === 'ACTIVE' ? 'green' : 'amber'}`;

        const isPlatformSecure = plat.firewall === 'ACTIVE' && plat.appProtectionShield === 'ACTIVE';
        const platBadge = document.getElementById('platformStatusIndicator');
        platBadge.textContent = isPlatformSecure ? 'SECURE' : 'VULNERABLE';
        platBadge.className = `status-indicator-badge ${isPlatformSecure ? 'green' : 'amber'}`;

        // Update Access Control Layer
        const acc = status.accessControl;
        document.getElementById('activeSessionsText').textContent = `${acc.activeSessions} Sessions`;

        // Update Network Layer
        const net = status.network;
        document.getElementById('ddosText').textContent = net.ddosMitigation;
        document.getElementById('ddosText').className = `metric-value font-bold ${net.ddosMitigation === 'ENABLED' ? 'green' : 'amber'}`;
        
        document.getElementById('blockedIpsText').textContent = `${net.blockedIps} IPs`;

        const isNetworkSecure = net.ddosMitigation === 'ENABLED';
        const netBadge = document.getElementById('networkStatusIndicator');
        netBadge.textContent = isNetworkSecure ? 'MITIGATING' : 'UNGUARDED';
        netBadge.className = `status-indicator-badge ${isNetworkSecure ? 'green' : 'amber'}`;

        // Update Performance Monitoring (CPU/RAM bars)
        const mon = status.monitoring;
        document.getElementById('cpuUsageVal').textContent = `${mon.cpuUsage}%`;
        document.getElementById('cpuBarFill').style.width = `${mon.cpuUsage}%`;
        
        document.getElementById('ramUsageVal').textContent = `${mon.memoryUsage}%`;
        document.getElementById('ramBarFill').style.width = `${mon.memoryUsage}%`;

        // Update health alert message
        const healthText = document.getElementById('systemHealthText');
        const overallHealthy = mon.systemStatus === 'HEALTHY';
        healthText.className = `monitoring-alert ${overallHealthy ? 'green' : 'amber'} margin-top`;
        healthText.innerHTML = overallHealthy 
            ? `<i data-lucide="check-circle-2"></i> Core systems reporting HEALTHY (0 latency drops).`
            : `<i data-lucide="alert-circle"></i> Warning: Some platform shields are disabled. System reports VULNERABLE.`;

        if (window.lucide) window.lucide.createIcons();
    });
}

function toggleShieldConfig(layer, currentTextElementId) {
    if (!currentUser) return;
    const currentVal = document.getElementById(currentTextElementId).textContent;
    const enableNext = !(currentVal === 'ACTIVE' || currentVal === 'ENABLED');

    const params = new URLSearchParams();
    params.append('layer', layer);
    params.append('enable', enableNext);
    params.append('username', currentUser.username);

    fetch(`${API_BASE}/security/shield`, {
        method: 'POST',
        body: params
    })
    .then(res => res.json())
    .then(() => {
        loadSecurityDashboard();
    })
    .catch(err => {
        console.error("Shield toggle failed:", err);
    });
}

// 5. Modals Controls
function showLoginModal() {
    const modal = document.getElementById('loginModal');
    if (modal) {
        modal.classList.remove('hidden');
        modal.classList.add('active');
    }
}

function hideLoginModal() {
    const modal = document.getElementById('loginModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('active');
    }
}

function showSignupModal() {
    hideLoginModal();
    const modal = document.getElementById('signupModal');
    if (modal) {
        modal.classList.remove('hidden');
        modal.classList.add('active');
    }
    if (window.lucide) window.lucide.createIcons();
}

function hideSignupModal() {
    const modal = document.getElementById('signupModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('active');
    }
}

window.showLoginModal = showLoginModal;
window.hideLoginModal = hideLoginModal;
window.showSignupModal = showSignupModal;
window.hideSignupModal = hideSignupModal;

window.openVideoModal = function(url, title) {
    document.getElementById('videoModalTitle').textContent = title;
    document.getElementById('videoIframe').src = url;
    document.getElementById('videoModal').classList.remove('hidden');
    document.getElementById('videoModal').classList.add('active');
};

function closeVideoModal() {
    document.getElementById('videoIframe').src = '';
    document.getElementById('videoModal').classList.add('hidden');
    document.getElementById('videoModal').classList.remove('active');
}

// 6. Global Event Handlers Setup
function setupEventHandlers() {
    // Login form submit
    document.getElementById('loginForm').addEventListener('submit', (e) => {
        e.preventDefault();
        const email = document.getElementById('loginEmailInput').value.trim();
        const password = document.getElementById('loginPasswordInput').value;
        loginUser(email, password);
    });

    // Signup form submit
    const signupForm = document.getElementById('signupForm');
    if (signupForm) {
        signupForm.addEventListener('submit', handleSignupSubmit);
    }

    // Public header/hero triggers
    document.getElementById('publicLoginBtn').addEventListener('click', showLoginModal);
    document.getElementById('heroLoginBtn').addEventListener('click', showLoginModal);
    document.getElementById('closeLoginModalBtn').addEventListener('click', hideLoginModal);

    // Signup triggers
    const pubSignupBtn = document.getElementById('publicSignupBtn');
    if (pubSignupBtn) pubSignupBtn.addEventListener('click', showSignupModal);

    const closeSignupBtn = document.getElementById('closeSignupModalBtn');
    if (closeSignupBtn) closeSignupBtn.addEventListener('click', hideSignupModal);

    const openSignupFromLoginBtn = document.getElementById('openSignupFromLoginBtn');
    if (openSignupFromLoginBtn) openSignupFromLoginBtn.addEventListener('click', showSignupModal);

    const openLoginFromSignupBtn = document.getElementById('openLoginFromSignupBtn');
    if (openLoginFromSignupBtn) openLoginFromSignupBtn.addEventListener('click', () => {
        hideSignupModal();
        showLoginModal();
    });

    const signupModalOverlay = document.getElementById('signupModal');
    if (signupModalOverlay) {
        signupModalOverlay.addEventListener('click', (e) => {
            if (e.target === signupModalOverlay) hideSignupModal();
        });
    }

    const loginModalOverlay = document.getElementById('loginModal');
    if (loginModalOverlay) {
        loginModalOverlay.addEventListener('click', (e) => {
            if (e.target === loginModalOverlay) hideLoginModal();
        });
    }

    // Refresh pending members in Control Center
    const refreshPendingBtn = document.getElementById('refreshPendingMembersBtn');
    if (refreshPendingBtn) refreshPendingBtn.addEventListener('click', loadPendingMembers);

    // Sidebar trigger
    document.getElementById('logoutBtn').addEventListener('click', logout);

    // Profile handles sync form (if present on view)
    const profileSyncForm = document.getElementById('profileSyncForm');
    if (profileSyncForm) {
        profileSyncForm.addEventListener('submit', (e) => {
            e.preventDefault();
            if (!currentUser) return;
            
            const gh = document.getElementById('githubInput') ? document.getElementById('githubInput').value.trim() : '';
            const lc = document.getElementById('leetcodeInput') ? document.getElementById('leetcodeInput').value.trim() : '';

            const params = new URLSearchParams();
            params.append('github', gh);
            params.append('leetcode', lc);

            fetch(`${API_BASE}/users/${encodeURIComponent(currentUser.username)}/profiles`, {
                method: 'POST',
                body: params
            })
            .then(res => {
                if (!res.ok) throw new Error("Could not update profiles");
                return res.json();
            })
            .then(updatedUser => {
                currentUser = updatedUser;
                alert("External handles linked successfully!");
                loadDashboardData();
            })
            .catch(err => {
                console.error(err);
                alert("Error saving profile links.");
            });
        });
    }

    // Profile activity sync button (if present on view)
    const syncProfilesBtn = document.getElementById('syncProfilesBtn');
    if (syncProfilesBtn) {
        syncProfilesBtn.addEventListener('click', () => {
            if (!currentUser) return;
            
            const btn = document.getElementById('syncProfilesBtn');
            const status = document.getElementById('syncStatusMsg');
            btn.disabled = true;
            btn.innerHTML = '<i data-lucide="loader" class="animate-spin"></i> Syncing Github & Leetcode...';
            if (window.lucide) window.lucide.createIcons();

            fetch(`${API_BASE}/users/${encodeURIComponent(currentUser.username)}/sync`, {
                method: 'POST'
            })
            .then(res => {
                if (!res.ok) throw new Error("Sync failed");
                return res.json();
            })
            .then(updatedUser => {
                currentUser = updatedUser;
                setTimeout(() => {
                    btn.disabled = false;
                    btn.innerHTML = '<i data-lucide="refresh-cw"></i> Sync Coding Activity';
                    if (status) status.textContent = `Last synced: Just now (Received +25 XP!)`;
                    updateUIForLoggedInState();
                    loadDashboardData();
                    if (window.lucide) window.lucide.createIcons();
                }, 1000); // Small mock delay for visual effect
            })
            .catch(err => {
                console.error(err);
                btn.disabled = false;
                btn.innerHTML = '<i data-lucide="refresh-cw"></i> Sync Coding Activity';
                if (status) status.textContent = 'Sync connection timed out.';
                if (window.lucide) window.lucide.createIcons();
            });
        });
    }

    // Refresh leaderboard
    document.getElementById('refreshLeaderboardBtn').addEventListener('click', () => {
        const icon = document.querySelector('#refreshLeaderboardBtn i');
        icon.style.transform = 'rotate(360deg)';
        loadLeaderboard();
        setTimeout(() => { icon.style.transform = ''; }, 300);
    });

    // Quizzes Category cards click
    const catCards = document.querySelectorAll('.category-card, .category-card-srm');
    catCards.forEach(card => {
        card.addEventListener('click', () => {
            const lang = card.getAttribute('data-lang');
            startQuiz(lang);
        });
    });

    // Quit Quiz
    document.getElementById('quitQuizBtn').addEventListener('click', () => {
        if (confirm("Are you sure you want to quit the quiz? Progress will be lost.")) {
            resetQuizScreen();
        }
    });

    // Quiz options click event delegate
    const optionBtns = document.querySelectorAll('.option-btn');
    optionBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const selected = btn.getAttribute('data-option');
            handleQuizAnswerSubmit(selected);
        });
    });

    // Finish Quiz button click
    document.getElementById('finishQuizBtn').addEventListener('click', resetQuizScreen);

    // Collaboration modals
    document.getElementById('openProposeProjectModalBtn').addEventListener('click', () => {
        if (!currentUser) {
            showLoginModal();
            return;
        }
        document.getElementById('proposeProjectModal').classList.remove('hidden');
        document.getElementById('proposeProjectModal').classList.add('active');
    });

    const closeProposeModal = () => {
        document.getElementById('proposeProjectModal').classList.add('hidden');
        document.getElementById('proposeProjectModal').classList.remove('active');
    };
    document.getElementById('closeProposeModalBtn').addEventListener('click', closeProposeModal);

    // Propose project submission
    document.getElementById('proposeProjectForm').addEventListener('submit', (e) => {
        e.preventDefault();
        if (!currentUser) return;

        const name = document.getElementById('newProjName').value.trim();
        const desc = document.getElementById('newProjDesc').value.trim();
        const git = document.getElementById('newProjGithub').value.trim();

        const projectBody = {
            name: name,
            description: desc,
            githubLink: git
        };

        fetch(`${API_BASE}/projects?username=${encodeURIComponent(currentUser.username)}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(projectBody)
        })
        .then(res => {
            if (!res.ok) throw new Error("Creation failed");
            return res.json();
        })
        .then(() => {
            closeProposeModal();
            // Reset form
            document.getElementById('proposeProjectForm').reset();
            loadProjectsList();
            alert("Project proposed successfully! Team lead set as " + currentUser.username);
        })
        .catch(err => {
            console.error(err);
            alert("Error proposing project.");
        });
    });

    // Video modal close
    document.getElementById('closeVideoModalBtn').addEventListener('click', closeVideoModal);

    // Security controls shield toggling
    document.getElementById('toggleFirewallBtn').addEventListener('click', () => {
        toggleShieldConfig('firewall', 'firewallText');
    });
    document.getElementById('toggleAppShieldBtn').addEventListener('click', () => {
        toggleShieldConfig('app', 'appShieldText');
    });
    document.getElementById('toggleDdosBtn').addEventListener('click', () => {
        toggleShieldConfig('ddos', 'ddosText');
    });

    // Trigger backup
    document.getElementById('triggerBackupBtn').addEventListener('click', () => {
        if (!currentUser) return;
        const btn = document.getElementById('triggerBackupBtn');
        btn.disabled = true;
        btn.innerHTML = '<i class="loader"></i> Creating system backup...';
        
        fetch(`${API_BASE}/security/backup?username=${encodeURIComponent(currentUser.username)}`, {
            method: 'POST'
        })
        .then(res => res.json())
        .then(data => {
            setTimeout(() => {
                btn.disabled = false;
                btn.innerHTML = '<i data-lucide="save"></i> Trigger System Backup';
                alert(data.message);
                loadSecurityDashboard();
                if (window.lucide) window.lucide.createIcons();
            }, 1000);
        });
    });

    // Trigger restore
    document.getElementById('triggerRestoreBtn').addEventListener('click', () => {
        if (!currentUser) return;
        if (!confirm("Are you sure you want to perform a system rollback? Active sessions might experience cache drops.")) return;
        
        const btn = document.getElementById('triggerRestoreBtn');
        btn.disabled = true;
        btn.innerHTML = '<i class="loader"></i> Triggering database rollback...';

        fetch(`${API_BASE}/security/restore?username=${encodeURIComponent(currentUser.username)}`, {
            method: 'POST'
        })
        .then(res => res.json())
        .then(data => {
            setTimeout(() => {
                btn.disabled = false;
                btn.innerHTML = '<i data-lucide="rotate-ccw"></i> Perform Emergency Restore';
                alert(data.message);
                loadSecurityDashboard();
                if (window.lucide) window.lucide.createIcons();
            }, 1000);
        });
    });

    // Leaderboard overall standings search filter
    const searchInput = document.getElementById('leaderboardSearchInput');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase();
            const rows = document.querySelectorAll('#leaderboardFullList tr');
            rows.forEach(row => {
                const name = row.children[1].textContent.toLowerCase();
                if (name.includes(query)) {
                    row.classList.remove('hidden');
                } else {
                    row.classList.add('hidden');
                }
            });
        });
    }

    // Resources Hub search filter
    const resourceSearch = document.getElementById('resourceSearchInput');
    if (resourceSearch) {
        resourceSearch.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase();
            const cards = document.querySelectorAll('#resourcesGrid .resource-card-srm');
            cards.forEach(card => {
                const title = card.querySelector('h4').textContent.toLowerCase();
                const desc = card.querySelector('p').textContent.toLowerCase();
                if (title.includes(query) || desc.includes(query)) {
                    card.classList.remove('hidden');
                } else {
                    card.classList.add('hidden');
                }
            });
        });
    }

    // Profile page event handlers
    const sidebarUserProfile = document.getElementById('sidebarUserProfile');
    if (sidebarUserProfile) {
        sidebarUserProfile.style.cursor = 'pointer';
        sidebarUserProfile.addEventListener('click', (e) => {
            // Avoid conflict if clicking logout button
            if (e.target.closest('#logoutBtn')) return;
            window.location.hash = '#profile';
        });
    }

    const openEditBtn = document.getElementById('openEditProfileBtn');
    if (openEditBtn) openEditBtn.addEventListener('click', openEditProfileModal);

    const closeEditBtn = document.getElementById('closeEditProfileModalBtn');
    if (closeEditBtn) closeEditBtn.addEventListener('click', closeEditProfileModal);

    const editForm = document.getElementById('editProfileForm');
    if (editForm) editForm.addEventListener('submit', handleProfileUpdate);

    const syncBtn = document.getElementById('syncPlatformsBtn');
    if (syncBtn) syncBtn.addEventListener('click', syncAllPlatforms);

    const openAddAnnounceBtn = document.getElementById('openAddAnnouncementBtn');
    if (openAddAnnounceBtn) openAddAnnounceBtn.addEventListener('click', openAnnouncementModal);

    const closeAddAnnounceBtn = document.getElementById('closeAnnouncementModalBtn');
    if (closeAddAnnounceBtn) closeAddAnnounceBtn.addEventListener('click', closeAnnouncementModal);

    const newAnnounceForm = document.getElementById('newAnnouncementForm');
    if (newAnnounceForm) newAnnounceForm.addEventListener('submit', handleAnnouncementSubmit);

    // Backdrop click to close modals
    const editModalOverlay = document.getElementById('editProfileModal');
    if (editModalOverlay) {
        editModalOverlay.addEventListener('click', (e) => {
            if (e.target === editModalOverlay) closeEditProfileModal();
        });
    }

    const announceModalOverlay = document.getElementById('announcementModal');
    if (announceModalOverlay) {
        announceModalOverlay.addEventListener('click', (e) => {
            if (e.target === announceModalOverlay) closeAnnouncementModal();
        });
    }

    // Platform Verification Modal listeners
    const closeVerifyBtn = document.getElementById('closeVerifyModalBtn');
    if (closeVerifyBtn) closeVerifyBtn.addEventListener('click', closeVerifyModal);

    const copyTokBtn = document.getElementById('copyTokenBtn');
    if (copyTokBtn) copyTokBtn.addEventListener('click', copyVerificationToken);

    const confirmBioBtn = document.getElementById('confirmBioVerificationBtn');
    if (confirmBioBtn) confirmBioBtn.addEventListener('click', () => submitPlatformVerification(false));

    const instantDemoBtn = document.getElementById('instantVerifyDemoBtn');
    if (instantDemoBtn) instantDemoBtn.addEventListener('click', () => submitPlatformVerification(true));

    const verifyModalOverlay = document.getElementById('verifyPlatformModal');
    if (verifyModalOverlay) {
        verifyModalOverlay.addEventListener('click', (e) => {
            if (e.target === verifyModalOverlay) closeVerifyModal();
        });
    }
}

// ==========================================
// PROFILE & PLATFORM ACTIVITY CONTROLLERS
// ==========================================

function loadProfilePageData() {
    if (!currentUser) return;

    fetch(`${API_BASE}/users/${encodeURIComponent(currentUser.username)}`)
        .then(res => {
            if (!res.ok) throw new Error("Failed to fetch profile");
            return res.json();
        })
        .then(user => {
            currentUser = user;
            updateUIForLoggedInState();

            // Populate Banner
            const avatarElem = document.getElementById('profileAvatar');
            if (avatarElem) {
                const initial = (user.fullName && user.fullName.trim()) 
                    ? user.fullName.trim().charAt(0).toUpperCase()
                    : user.username.charAt(0).toUpperCase();
                avatarElem.textContent = initial;
            }

            const nameElem = document.getElementById('profileFullName');
            if (nameElem) nameElem.textContent = user.fullName || user.username;

            const roleElem = document.getElementById('profileRoleBadge');
            if (roleElem) {
                roleElem.textContent = (user.role === 'ADMIN' || (user.username && user.username.toLowerCase() === 'lakshya')) ? 'Admin' : 'Member';
            }

            const yearElem = document.getElementById('profileYearBadge');
            if (yearElem) yearElem.textContent = user.academicYear || '2nd Year';

            const emojiElem = document.getElementById('profileStatusEmoji');
            if (emojiElem) emojiElem.textContent = user.statusEmoji || '🤩';

            const bioElem = document.getElementById('profileBio');
            if (bioElem) bioElem.textContent = user.bio || 'Core Member & Competitive Programmer @ MEDSTELLAR';

            const emailElem = document.getElementById('profileEmail');
            if (emailElem) emailElem.textContent = user.email || 'lj3384@srmist.edu.in';

            // Metrics
            const totalPointsElem = document.getElementById('profileTotalPoints');
            if (totalPointsElem) totalPointsElem.textContent = `${(user.points || 0).toLocaleString()} XP`;

            const rankTitleElem = document.getElementById('profileRankTitle');
            if (rankTitleElem) rankTitleElem.textContent = user.rankName || 'Grandmaster';

            const prevRankElem = document.getElementById('profilePreviousRank');
            if (prevRankElem) prevRankElem.textContent = `#${user.previousRank || 19}`;

            // Compute current leaderboard standing
            fetch(`${API_BASE}/users/leaderboard`)
                .then(r => r.json())
                .then(allUsers => {
                    const myIndex = allUsers.findIndex(u => u.username === user.username);
                    const currentRankElem = document.getElementById('profileCurrentRank');
                    if (currentRankElem) {
                        currentRankElem.textContent = myIndex !== -1 ? `#${myIndex + 1}` : '#18';
                    }
                })
                .catch(() => {
                    const currentRankElem = document.getElementById('profileCurrentRank');
                    if (currentRankElem) currentRankElem.textContent = '#18';
                });

            // Platform Handles & Stats
            // LeetCode
            const lcUserElem = document.getElementById('displayLeetcodeUser');
            if (lcUserElem) lcUserElem.textContent = user.leetcodeUsername || 'Not Connected';
            const lcBadges = document.getElementById('lcBadges');
            if (lcBadges) lcBadges.textContent = user.leetcodeBadges !== null ? user.leetcodeBadges : 1;
            const lcRank = document.getElementById('lcRank');
            if (lcRank) lcRank.textContent = user.leetcodeRank || '1418643';
            const lcSolved = document.getElementById('lcSolved');
            if (lcSolved) lcSolved.textContent = user.leetcodeSolved !== null ? user.leetcodeSolved : 120;
            const lcLink = document.getElementById('lcExternalLink');
            if (lcLink) lcLink.href = user.leetcodeUsername ? `https://leetcode.com/u/${user.leetcodeUsername}` : 'https://leetcode.com';
            const lcStatusElem = document.getElementById('lcVerifyStatus');
            if (lcStatusElem) {
                if (user.leetcodeVerified) {
                    lcStatusElem.innerHTML = `<span class="verified-status-chip" title="Account Ownership Confirmed">✓ Verified</span>`;
                } else if (user.leetcodeUsername) {
                    lcStatusElem.innerHTML = `<button type="button" class="btn-verify-chip" onclick="openVerifyModal('leetcode')"><i data-lucide="shield-alert"></i> Verify</button>`;
                } else {
                    lcStatusElem.innerHTML = `<button type="button" class="btn-verify-chip not-linked" onclick="openEditProfileModal()"><i data-lucide="link"></i> Link</button>`;
                }
            }

            // CodeChef
            const ccUserElem = document.getElementById('displayCodechefUser');
            if (ccUserElem) ccUserElem.textContent = user.codechefUsername || 'Not Connected';
            const ccStars = document.getElementById('ccStars');
            if (ccStars) ccStars.textContent = user.codechefStars || '1 Star(s)';
            const ccRank = document.getElementById('ccRank');
            if (ccRank) ccRank.textContent = user.codechefRank || '11961455';
            const ccRating = document.getElementById('ccRating');
            if (ccRating) ccRating.textContent = user.codechefRating !== null ? user.codechefRating : 1196;
            const ccLink = document.getElementById('ccExternalLink');
            if (ccLink) ccLink.href = user.codechefUsername ? `https://www.codechef.com/users/${user.codechefUsername}` : 'https://www.codechef.com';
            const ccStatusElem = document.getElementById('ccVerifyStatus');
            if (ccStatusElem) {
                if (user.codechefVerified) {
                    ccStatusElem.innerHTML = `<span class="verified-status-chip" title="Account Ownership Confirmed">✓ Verified</span>`;
                } else if (user.codechefUsername) {
                    ccStatusElem.innerHTML = `<button type="button" class="btn-verify-chip" onclick="openVerifyModal('codechef')"><i data-lucide="shield-alert"></i> Verify</button>`;
                } else {
                    ccStatusElem.innerHTML = `<button type="button" class="btn-verify-chip not-linked" onclick="openEditProfileModal()"><i data-lucide="link"></i> Link</button>`;
                }
            }

            // Codeforces
            const cfUserElem = document.getElementById('displayCodeforcesUser');
            if (cfUserElem) cfUserElem.textContent = user.codeforcesUsername || 'Not Connected';
            const cfRating = document.getElementById('cfRating');
            if (cfRating) cfRating.textContent = user.codeforcesRating !== null ? user.codeforcesRating : 0;
            const cfRank = document.getElementById('cfRank');
            if (cfRank) cfRank.textContent = user.codeforcesRank || 'unrated';
            const cfSolved = document.getElementById('cfSolved');
            if (cfSolved) cfSolved.textContent = user.codeforcesSolved !== null ? user.codeforcesSolved : 0;
            const cfLink = document.getElementById('cfExternalLink');
            if (cfLink) cfLink.href = user.codeforcesUsername ? `https://codeforces.com/profile/${user.codeforcesUsername}` : 'https://codeforces.com';
            const cfStatusElem = document.getElementById('cfVerifyStatus');
            if (cfStatusElem) {
                if (user.codeforcesVerified) {
                    cfStatusElem.innerHTML = `<span class="verified-status-chip" title="Account Ownership Confirmed">✓ Verified</span>`;
                } else if (user.codeforcesUsername) {
                    cfStatusElem.innerHTML = `<button type="button" class="btn-verify-chip" onclick="openVerifyModal('codeforces')"><i data-lucide="shield-alert"></i> Verify</button>`;
                } else {
                    cfStatusElem.innerHTML = `<button type="button" class="btn-verify-chip not-linked" onclick="openEditProfileModal()"><i data-lucide="link"></i> Link</button>`;
                }
            }

            // GeeksforGeeks
            const gfgUserElem = document.getElementById('displayGfgUser');
            if (gfgUserElem) gfgUserElem.textContent = user.gfgUsername || 'Not Connected';
            const gfgRank = document.getElementById('gfgRank');
            if (gfgRank) gfgRank.textContent = user.gfgRank !== null ? user.gfgRank : 37;
            const gfgScore = document.getElementById('gfgScore');
            if (gfgScore) gfgScore.textContent = user.gfgScore !== null ? user.gfgScore : 66;
            const gfgSolved = document.getElementById('gfgSolved');
            if (gfgSolved) gfgSolved.textContent = user.gfgSolved !== null ? user.gfgSolved : 47;
            const gfgLink = document.getElementById('gfgExternalLink');
            if (gfgLink) gfgLink.href = user.gfgUsername ? `https://auth.geeksforgeeks.org/user/${user.gfgUsername}` : 'https://geeksforgeeks.org';
            const gfgStatusElem = document.getElementById('gfgVerifyStatus');
            if (gfgStatusElem) {
                if (user.gfgVerified) {
                    gfgStatusElem.innerHTML = `<span class="verified-status-chip" title="Account Ownership Confirmed">✓ Verified</span>`;
                } else if (user.gfgUsername) {
                    gfgStatusElem.innerHTML = `<button type="button" class="btn-verify-chip" onclick="openVerifyModal('gfg')"><i data-lucide="shield-alert"></i> Verify</button>`;
                } else {
                    gfgStatusElem.innerHTML = `<button type="button" class="btn-verify-chip not-linked" onclick="openEditProfileModal()"><i data-lucide="link"></i> Link</button>`;
                }
            }

            // Calendar Heatmap & Stats
            renderProfileCalendar(user);

            // Announcements
            loadAnnouncementsFeed();

            if (window.lucide) window.lucide.createIcons();
        })
        .catch(err => console.error("Failed to load profile data:", err));
}

function renderProfileCalendar(user) {
    const grid = document.getElementById('profileCalendarHeatGrid');
    if (!grid) return;
    grid.innerHTML = '';

    // Platform solves from linked profiles
    const lcSolved = (user.leetcodeUsername && user.leetcodeSolved != null) ? Number(user.leetcodeSolved) : 0;
    const ccSolved = (user.codechefUsername && user.codechefSolved != null) 
        ? Number(user.codechefSolved)
        : ((user.codechefUsername && user.codechefRating && user.codechefRating > 0)
            ? Math.round(user.codechefRating / 35)
            : (user.codechefUsername ? 34 : 0));
    const cfSolved = (user.codeforcesUsername && user.codeforcesSolved != null) ? Number(user.codeforcesSolved) : 0;
    const gfgSolved = (user.gfgUsername && user.gfgSolved != null) ? Number(user.gfgSolved) : 0;

    const totalCareerSolved = lcSolved + ccSolved + cfSolved + gfgSolved;

    // Calendar for September 2026 (Starts on Tuesday, offset 2, 30 days)
    const firstDayOffset = 2;
    const totalDaysInMonth = 30;

    // Lead-in empty cells
    for (let i = 0; i < firstDayOffset; i++) {
        const emptyCell = document.createElement('div');
        emptyCell.className = 'heat-cell heat-lvl-0';
        emptyCell.style.opacity = '0.3';
        grid.appendChild(emptyCell);
    }

    const maxSolvedElem = document.getElementById('profileCalMaxSolved');
    const streakElem = document.getElementById('profileCalDayStreak');
    const avgElem = document.getElementById('profileCalAvgSolved');

    // Case 1: No linked platform activity or 0 solves
    const now = new Date();
    const currentDay = Math.min(totalDaysInMonth, now.getDate() || 15);

    if (totalCareerSolved === 0) {
        for (let day = 1; day <= totalDaysInMonth; day++) {
            const cell = document.createElement('div');
            cell.className = 'heat-cell heat-lvl-0';
            cell.textContent = day;
            if (day > currentDay) {
                cell.classList.add('future-day');
                cell.style.opacity = '0.3';
                cell.title = `Sept ${day}: Upcoming (No activity yet)`;
            } else {
                cell.title = `Sept ${day}: 0 questions solved (No linked platform activity)`;
            }
            grid.appendChild(cell);
        }
        if (maxSolvedElem) maxSolvedElem.textContent = '0';
        if (streakElem) streakElem.textContent = '0';
        if (avgElem) avgElem.textContent = '0.00';
        return;
    }

    // Case 2: Distribute problem solves strictly across elapsed days up to today (day <= currentDay)
    // Scale monthly solves proportional to elapsed days in the month
    const fullMonthSolvedEstimate = Math.max(
        user.streak || 1,
        Math.min(totalCareerSolved, Math.max(1, Math.round(totalCareerSolved * 0.22)))
    );
    const elapsedMonthSolves = Math.max(
        user.streak || 1,
        Math.min(fullMonthSolvedEstimate, Math.round(fullMonthSolvedEstimate * (currentDay / totalDaysInMonth)))
    );

    // Prepare solve items by platform
    const platformSolvesPool = [];
    if (lcSolved > 0) {
        const count = Math.max(1, Math.round(elapsedMonthSolves * (lcSolved / totalCareerSolved)));
        for (let i = 0; i < count; i++) platformSolvesPool.push('LeetCode');
    }
    if (ccSolved > 0) {
        const count = Math.max(1, Math.round(elapsedMonthSolves * (ccSolved / totalCareerSolved)));
        for (let i = 0; i < count; i++) platformSolvesPool.push('CodeChef');
    }
    if (cfSolved > 0) {
        const count = Math.max(1, Math.round(elapsedMonthSolves * (cfSolved / totalCareerSolved)));
        for (let i = 0; i < count; i++) platformSolvesPool.push('Codeforces');
    }
    if (gfgSolved > 0) {
        const count = Math.max(1, Math.round(elapsedMonthSolves * (gfgSolved / totalCareerSolved)));
        for (let i = 0; i < count; i++) platformSolvesPool.push('GeeksforGeeks');
    }

    // Days map: strictly 1..totalDaysInMonth
    const dailySolves = {};
    for (let d = 1; d <= totalDaysInMonth; d++) {
        dailySolves[d] = { total: 0, breakdown: {} };
    }

    // Active streak ends strictly on currentDay (today)
    const streakDays = Math.max(0, user.streak || 1);
    const streakStart = Math.max(1, currentDay - streakDays + 1);

    // Active candidate past days strictly <= currentDay (NEVER in the future)
    const baseCandidateDays = [1, 2, 4, 6, 8, 9, 11, 14, 15].filter(d => d <= currentDay);
    const activeDaysSet = new Set(baseCandidateDays);
    for (let d = streakStart; d <= currentDay; d++) {
        activeDaysSet.add(d);
    }
    const activeDaysList = Array.from(activeDaysSet).sort((a, b) => a - b);

    // Ensure streak days leading up to today have active solves
    for (let d = streakStart; d <= currentDay; d++) {
        const plat = platformSolvesPool.pop() || (lcSolved > 0 ? 'LeetCode' : (gfgSolved > 0 ? 'GeeksforGeeks' : (ccSolved > 0 ? 'CodeChef' : 'Codeforces')));
        dailySolves[d].total += 1;
        dailySolves[d].breakdown[plat] = (dailySolves[d].breakdown[plat] || 0) + 1;
    }

    // Distribute remaining pool strictly among past/present active days
    let dayCursor = 0;
    while (platformSolvesPool.length > 0 && activeDaysList.length > 0) {
        const day = activeDaysList[dayCursor % activeDaysList.length];
        const plat = platformSolvesPool.pop();
        dailySolves[day].total += 1;
        dailySolves[day].breakdown[plat] = (dailySolves[day].breakdown[plat] || 0) + 1;
        dayCursor++;
    }

    // Render cells and calculate live calendar metrics
    let maxSolvesInDay = 0;
    let totalRenderedSolves = 0;

    for (let day = 1; day <= totalDaysInMonth; day++) {
        const dayData = dailySolves[day];
        const count = dayData.total;
        totalRenderedSolves += count;
        if (count > maxSolvesInDay) maxSolvesInDay = count;

        let lvl = 0;
        if (count === 1) lvl = 1;
        else if (count >= 2 && count <= 3) lvl = 2;
        else if (count >= 4) lvl = 3;

        const cell = document.createElement('div');
        cell.textContent = day;

        if (day > currentDay) {
            // Future day: strictly 0 solves, upcoming indicator
            cell.className = 'heat-cell heat-lvl-0 future-day';
            cell.style.opacity = '0.3';
            cell.title = `Sept ${day}: Upcoming (No activity yet)`;
        } else {
            cell.className = `heat-cell heat-lvl-${lvl}`;
            if (count === 0) {
                cell.title = `Sept ${day}: No questions solved`;
            } else {
                const breakdownParts = Object.entries(dayData.breakdown).map(([plat, num]) => `${plat}: ${num}`);
                cell.title = `Sept ${day}: ${count} question${count > 1 ? 's' : ''} solved (${breakdownParts.join(', ')})`;
            }
        }
        grid.appendChild(cell);
    }

    // Update bottom stats dynamically
    if (maxSolvedElem) maxSolvedElem.textContent = maxSolvesInDay;
    if (streakElem) streakElem.textContent = user.streak || streakDays;
    if (avgElem) {
        // Average questions solved per elapsed day so far this month
        avgElem.textContent = (totalRenderedSolves / currentDay).toFixed(2);
    }
}

function loadAnnouncementsFeed() {
    const feed = document.getElementById('profileAnnouncementsList');
    if (!feed) return;

    // Show add button if Admin
    const addBtn = document.getElementById('openAddAnnouncementBtn');
    if (addBtn) {
        const isAdmin = currentUser && (
            currentUser.role === 'ADMIN' || 
            currentUser.admin === true || 
            (currentUser.username && currentUser.username.toLowerCase() === 'lakshya')
        );
        addBtn.style.display = isAdmin ? 'inline-flex' : 'none';
    }

    fetch(`${API_BASE}/users/announcements`)
        .then(res => res.json())
        .then(announcements => {
            feed.innerHTML = '';
            if (!announcements || announcements.length === 0) {
                feed.innerHTML = '<p class="text-muted text-sm" style="padding: 10px;">No announcements posted yet.</p>';
                return;
            }

            announcements.forEach(a => {
                const card = document.createElement('div');
                card.className = 'announcement-item-card';

                const dateStr = a.createdAt ? new Date(a.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) : 'Recently';

                card.innerHTML = `
                    <div class="announcement-meta-row">
                        <span class="announcement-tag-chip ${a.tag || 'ANNOUNCEMENT'}">${a.tag || 'ANNOUNCEMENT'}</span>
                        <span class="announcement-date">${dateStr}</span>
                    </div>
                    <h4 class="announcement-title">${a.title}</h4>
                    <p class="announcement-text">${a.content}</p>
                    <span class="announcement-author">— ${a.author || 'MEDSTELLAR Team'}</span>
                `;
                feed.appendChild(card);
            });
        })
        .catch(err => console.error("Error loading announcements:", err));
}

function syncAllPlatforms() {
    if (!currentUser) return;

    const syncBtn = document.getElementById('syncPlatformsBtn');
    if (syncBtn) {
        syncBtn.disabled = true;
        syncBtn.innerHTML = '<i data-lucide="loader" class="animate-spin"></i> Syncing...';
        if (window.lucide) window.lucide.createIcons();
    }

    fetch(`${API_BASE}/users/${encodeURIComponent(currentUser.username)}/sync-platforms`, {
        method: 'POST'
    })
    .then(res => {
        if (!res.ok) throw new Error("Sync failed");
        return res.json();
    })
    .then(updatedUser => {
        currentUser = updatedUser;
        updateUIForLoggedInState();
        loadProfilePageData();

        setTimeout(() => {
            if (syncBtn) {
                syncBtn.disabled = false;
                syncBtn.innerHTML = '<i data-lucide="refresh-cw"></i> Sync Platforms';
                if (window.lucide) window.lucide.createIcons();
            }
            alert(`🎉 Platforms Synced Successfully!\n\nAwarded EXP bonus across connected platforms!\nCurrent EXP: ${updatedUser.points} XP\nCurrent Streak: ${updatedUser.streak} Days`);
        }, 800);
    })
    .catch(err => {
        console.error("Platform sync error:", err);
        if (syncBtn) {
            syncBtn.disabled = false;
            syncBtn.innerHTML = '<i data-lucide="refresh-cw"></i> Sync Platforms';
            if (window.lucide) window.lucide.createIcons();
        }
        alert("Failed to complete platform sync. Please verify handle names.");
    });
}

function openEditProfileModal() {
    if (!currentUser) return;
    document.getElementById('inputFullName').value = currentUser.fullName || currentUser.username || '';
    document.getElementById('inputAcademicYear').value = currentUser.academicYear || '2nd Year';
    document.getElementById('inputStatusEmoji').value = currentUser.statusEmoji || '🤩';
    document.getElementById('inputBio').value = currentUser.bio || '';
    document.getElementById('inputLeetcodeHandle').value = currentUser.leetcodeUsername || '';
    document.getElementById('inputCodechefHandle').value = currentUser.codechefUsername || '';
    document.getElementById('inputCodeforcesHandle').value = currentUser.codeforcesUsername || '';
    document.getElementById('inputGfgHandle').value = currentUser.gfgUsername || '';
    document.getElementById('inputGithubHandle').value = currentUser.githubUsername || '';

    const modal = document.getElementById('editProfileModal');
    if (modal) {
        modal.classList.remove('hidden');
        modal.classList.add('active');
    }
    if (window.lucide) window.lucide.createIcons();
}

function closeEditProfileModal() {
    const modal = document.getElementById('editProfileModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('active');
    }
}

function handleProfileUpdate(e) {
    e.preventDefault();
    if (!currentUser) return;

    const saveBtn = e.target.querySelector('button[type="submit"]');
    if (saveBtn) {
        saveBtn.disabled = true;
        saveBtn.innerHTML = '<i data-lucide="loader" class="animate-spin"></i> Saving...';
        if (window.lucide) window.lucide.createIcons();
    }

    const params = new URLSearchParams();
    params.append('fullName', document.getElementById('inputFullName').value);
    params.append('academicYear', document.getElementById('inputAcademicYear').value);
    params.append('statusEmoji', document.getElementById('inputStatusEmoji').value);
    params.append('bio', document.getElementById('inputBio').value);
    params.append('leetcode', document.getElementById('inputLeetcodeHandle').value);
    params.append('codechef', document.getElementById('inputCodechefHandle').value);
    params.append('codeforces', document.getElementById('inputCodeforcesHandle').value);
    params.append('gfg', document.getElementById('inputGfgHandle').value);
    params.append('github', document.getElementById('inputGithubHandle').value);

    fetch(`${API_BASE}/users/${encodeURIComponent(currentUser.username)}/full-profile`, {
        method: 'POST',
        body: params
    })
    .then(res => {
        if (!res.ok) throw new Error("Failed to update profile");
        return res.json();
    })
    .then(updatedUser => {
        currentUser = updatedUser;
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = 'Save Changes & Sync EXP';
        }
        closeEditProfileModal();
        updateUIForLoggedInState();
        loadProfilePageData();
        syncAllPlatforms();
    })
    .catch(err => {
        console.error("Profile save error:", err);
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = 'Save Changes & Sync EXP';
        }
        alert("Error saving profile changes. Please try again.");
    });
}

function openAnnouncementModal() {
    document.getElementById('newAnnouncementForm').reset();
    const modal = document.getElementById('announcementModal');
    if (modal) {
        modal.classList.remove('hidden');
        modal.classList.add('active');
    }
    if (window.lucide) window.lucide.createIcons();
}

function closeAnnouncementModal() {
    const modal = document.getElementById('announcementModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('active');
    }
}

function handleAnnouncementSubmit(e) {
    e.preventDefault();
    if (!currentUser) return;

    const title = document.getElementById('newAnnounceTitle').value.trim();
    const tag = document.getElementById('newAnnounceTag').value;
    const content = document.getElementById('newAnnounceContent').value.trim();

    if (!title || !content) return;

    const payload = {
        title: title,
        tag: tag,
        content: content,
        author: currentUser.fullName ? `${currentUser.fullName} (${currentUser.role})` : currentUser.username
    };

    fetch(`${API_BASE}/users/announcements`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => {
        if (!res.ok) throw new Error("Failed to post announcement");
        return res.json();
    })
    .then(() => {
        closeAnnouncementModal();
        loadAnnouncementsFeed();
        alert("Announcement published successfully!");
    })
    .catch(err => {
        console.error("Announcement post error:", err);
        alert("Failed to post announcement.");
    });
}

// ==========================================
// 5. PLATFORM VERIFICATION CONTROLLER (BIO TOKEN)
// ==========================================

let currentVerifyPlatform = 'leetcode';

const PLATFORM_VERIFY_CONFIGS = {
    leetcode: {
        name: 'LeetCode',
        code: 'LC',
        badgeClass: 'leetcode-badge-logo',
        handleField: 'leetcodeUsername',
        settingsUrl: () => `https://leetcode.com/profile/`,
        instructions: 'Open your LeetCode profile and paste this token into your <strong>About Me / Summary</strong> section, then click "Save".'
    },
    codechef: {
        name: 'CodeChef',
        code: 'CC',
        badgeClass: 'codechef-badge-logo',
        handleField: 'codechefUsername',
        settingsUrl: (u) => u ? `https://www.codechef.com/users/${encodeURIComponent(u)}` : 'https://www.codechef.com',
        instructions: 'Open your CodeChef profile and paste this token anywhere inside your <strong>About Me / Bio</strong> section, then save.'
    },
    codeforces: {
        name: 'Codeforces',
        code: 'CF',
        badgeClass: 'codeforces-badge-logo',
        handleField: 'codeforcesUsername',
        settingsUrl: () => 'https://codeforces.com/settings/social',
        instructions: 'Open Codeforces Settings > Social and paste this token into your <strong>First Name / Last Name or Social bio</strong>, then click "Save Changes".'
    },
    gfg: {
        name: 'GeeksforGeeks',
        code: 'GFG',
        badgeClass: 'gfg-badge-logo',
        handleField: 'gfgUsername',
        settingsUrl: (u) => u ? `https://auth.geeksforgeeks.org/user/${encodeURIComponent(u)}/practice` : 'https://geeksforgeeks.org',
        instructions: 'Open your GeeksforGeeks profile edit page and paste this token into your <strong>Bio</strong> field, then click "Save Changes".'
    }
};

function openVerifyModal(platform) {
    if (!currentUser) return;
    currentVerifyPlatform = (platform || 'leetcode').toLowerCase();
    const config = PLATFORM_VERIFY_CONFIGS[currentVerifyPlatform] || PLATFORM_VERIFY_CONFIGS.leetcode;
    const handle = currentUser[config.handleField] || '';

    if (!handle) {
        alert(`Please enter and save your ${config.name} username in "Edit Profile" first before verifying.`);
        openEditProfileModal();
        return;
    }

    // Platform header info
    const iconElem = document.getElementById('verifyModalPlatformIcon');
    if (iconElem) {
        iconElem.textContent = config.code;
        iconElem.className = `platform-badge-logo ${config.badgeClass}`;
    }

    const titleElem = document.getElementById('verifyModalTitle');
    if (titleElem) titleElem.textContent = `Verify ${config.name} Ownership`;

    const subElem = document.getElementById('verifyModalSubtitle');
    if (subElem) subElem.textContent = `Confirm handle @${handle}`;

    const instrElem = document.getElementById('verifyStepInstructions');
    if (instrElem) instrElem.innerHTML = config.instructions;

    const linkElem = document.getElementById('verifyPlatformDirectLink');
    if (linkElem) linkElem.href = config.settingsUrl(handle);

    // Reset status alert box
    const statusBox = document.getElementById('verifyStatusBox');
    const statusText = document.getElementById('verifyStatusText');
    if (statusBox) {
        statusBox.classList.add('hidden');
        statusBox.classList.remove('error', 'success');
    }

    // Reset buttons
    const confirmBtn = document.getElementById('confirmBioVerificationBtn');
    if (confirmBtn) {
        confirmBtn.disabled = false;
        confirmBtn.innerHTML = '<i data-lucide="shield-check"></i> Check Bio & Confirm';
    }

    const instantBtn = document.getElementById('instantVerifyDemoBtn');
    if (instantBtn) {
        instantBtn.disabled = false;
        instantBtn.innerHTML = '<i data-lucide="zap"></i> Instant Verify (Demo)';
    }

    // Fetch or display token
    const tokenDisplay = document.getElementById('verifyTokenDisplay');
    if (tokenDisplay) {
        tokenDisplay.textContent = currentUser.verificationToken || 'MS-....';
    }

    fetch(`${API_BASE}/users/${encodeURIComponent(currentUser.username)}/verify-token`)
        .then(res => res.json())
        .then(data => {
            if (data.token && tokenDisplay) {
                tokenDisplay.textContent = data.token;
                currentUser.verificationToken = data.token;
            }
        })
        .catch(err => console.error("Error fetching token:", err));

    // Show modal
    const modal = document.getElementById('verifyPlatformModal');
    if (modal) {
        modal.classList.remove('hidden');
        modal.classList.add('active');
    }
    if (window.lucide) window.lucide.createIcons();
}

function closeVerifyModal() {
    const modal = document.getElementById('verifyPlatformModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('active');
    }
}

function copyVerificationToken() {
    const tokenElem = document.getElementById('verifyTokenDisplay');
    const btn = document.getElementById('copyTokenBtn');
    if (!tokenElem) return;
    const token = tokenElem.textContent.trim();

    const updateBtnSuccess = () => {
        if (btn) {
            const original = btn.innerHTML;
            btn.innerHTML = '<i data-lucide="check"></i> Copied!';
            if (window.lucide) window.lucide.createIcons();
            setTimeout(() => {
                btn.innerHTML = original;
                if (window.lucide) window.lucide.createIcons();
            }, 1600);
        }
    };

    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(token).then(updateBtnSuccess).catch(() => {
            copyTokenFallback(token, updateBtnSuccess);
        });
    } else {
        copyTokenFallback(token, updateBtnSuccess);
    }
}

function copyTokenFallback(token, cb) {
    const ta = document.createElement('textarea');
    ta.value = token;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    try {
        document.execCommand('copy');
        if (cb) cb();
    } catch (e) {
        prompt("Copy your verification token:", token);
    }
    document.body.removeChild(ta);
}

function submitPlatformVerification(demoBypass) {
    if (!currentUser) return;
    const platform = currentVerifyPlatform;
    const statusBox = document.getElementById('verifyStatusBox');
    const statusText = document.getElementById('verifyStatusText');
    const confirmBtn = document.getElementById('confirmBioVerificationBtn');
    const instantBtn = document.getElementById('instantVerifyDemoBtn');

    if (statusBox && statusText) {
        statusBox.classList.remove('hidden', 'error', 'success');
        statusText.innerHTML = demoBypass 
            ? '<i data-lucide="loader" class="animate-spin"></i> Performing instant demo verification...' 
            : '<i data-lucide="loader" class="animate-spin"></i> Scanning public profile bio for verification token...';
        if (window.lucide) window.lucide.createIcons();
    }

    if (confirmBtn) {
        confirmBtn.disabled = true;
        confirmBtn.innerHTML = '<i data-lucide="loader" class="animate-spin"></i> Verifying...';
    }
    if (instantBtn) {
        instantBtn.disabled = true;
    }
    if (window.lucide) window.lucide.createIcons();

    fetch(`${API_BASE}/users/${encodeURIComponent(currentUser.username)}/verify-platform?platform=${encodeURIComponent(platform)}&demoBypass=${demoBypass ? 'true' : 'false'}`, {
        method: 'POST'
    })
    .then(res => res.json())
    .then(data => {
        if (confirmBtn) {
            confirmBtn.disabled = false;
            confirmBtn.innerHTML = '<i data-lucide="shield-check"></i> Check Bio & Confirm';
        }
        if (instantBtn) {
            instantBtn.disabled = false;
            instantBtn.innerHTML = '<i data-lucide="zap"></i> Instant Verify (Demo)';
        }

        if (data.success) {
            if (statusBox && statusText) {
                statusBox.classList.remove('error');
                statusBox.classList.add('success');
                statusText.innerHTML = `<i data-lucide="check-circle-2"></i> ${data.message}`;
            }
            if (data.user) {
                currentUser = data.user;
            }
            if (window.lucide) window.lucide.createIcons();

            setTimeout(() => {
                closeVerifyModal();
                loadProfilePageData();
                updateUIForLoggedInState();
                alert(`🎉 Verification Successful!\n\n${data.message}\n+100 EXP has been added to your profile!`);
            }, 1200);
        } else {
            if (statusBox && statusText) {
                statusBox.classList.remove('success');
                statusBox.classList.add('error');
                statusText.innerHTML = `<i data-lucide="alert-triangle"></i> ${data.message || 'Token not detected. Please verify your bio is public and saved.'}`;
            }
            if (window.lucide) window.lucide.createIcons();
        }
    })
    .catch(err => {
        console.error("Verification call failed:", err);
        if (confirmBtn) {
            confirmBtn.disabled = false;
            confirmBtn.innerHTML = '<i data-lucide="shield-check"></i> Check Bio & Confirm';
        }
        if (instantBtn) {
            instantBtn.disabled = false;
            instantBtn.innerHTML = '<i data-lucide="zap"></i> Instant Verify (Demo)';
        }
        if (statusBox && statusText) {
            statusBox.classList.remove('success');
            statusBox.classList.add('error');
            statusText.textContent = "Network error during verification. Please try again.";
        }
        if (window.lucide) window.lucide.createIcons();
    });
}

// Global Exports
window.openVerifyModal = openVerifyModal;
window.closeVerifyModal = closeVerifyModal;
window.copyVerificationToken = copyVerificationToken;
window.submitPlatformVerification = submitPlatformVerification;
window.openEditProfileModal = openEditProfileModal;
window.closeEditProfileModal = closeEditProfileModal;

// ==========================================
// 6. MEMBER REGISTRATION & ADMISSION GOVERNANCE
// ==========================================

function handleSignupSubmit(e) {
    e.preventDefault();
    const fullName = document.getElementById('signupFullName').value.trim();
    const email = document.getElementById('signupEmail').value.trim();
    const password = document.getElementById('signupPassword').value;
    const academicYear = document.getElementById('signupAcademicYear').value;
    const bio = document.getElementById('signupBio').value.trim();

    const submitBtn = document.getElementById('submitSignupBtn');
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<i data-lucide="loader" class="animate-spin"></i> Submitting Application...';
        if (window.lucide) window.lucide.createIcons();
    }

    const params = new URLSearchParams();
    params.append('fullName', fullName);
    params.append('email', email);
    params.append('password', password);
    params.append('academicYear', academicYear);
    params.append('bio', bio);

    fetch(`${API_BASE}/users/register`, {
        method: 'POST',
        body: params
    })
    .then(async res => {
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(data.message || "Registration failed");
        }
        return data;
    })
    .then(data => {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = 'Submit Application for Review';
        }
        document.getElementById('signupForm').reset();
        hideSignupModal();
        alert(`🎉 Application Submitted Successfully!\n\nWelcome ${fullName}!\n\nYour application has been routed to Administrator Lakshya's Control Center for membership review.\nOnce approved, you will be able to log in with ${email} and access the full portal!`);
    })
    .catch(err => {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = 'Submit Application for Review';
        }
        alert(err.message || "Registration submission failed. Please try again.");
        console.error(err);
    });
}

function loadPendingMembers() {
    const list = document.getElementById('pendingMembersList');
    const table = document.getElementById('pendingMembersTable');
    const emptyMsg = document.getElementById('noPendingMembersMsg');
    const badge = document.getElementById('pendingApplicantsCountBadge');

    if (!list) return;

    fetch(`${API_BASE}/users/pending`)
        .then(res => res.json())
        .then(users => {
            const count = users ? users.length : 0;
            if (badge) {
                badge.textContent = `${count} Pending`;
                badge.className = `badge-growth-chip ${count > 0 ? 'amber-chip' : 'cyan-chip'}`;
            }

            list.innerHTML = '';
            if (!users || users.length === 0) {
                if (table) table.classList.add('hidden');
                if (emptyMsg) emptyMsg.classList.remove('hidden');
                return;
            }

            if (table) table.classList.remove('hidden');
            if (emptyMsg) emptyMsg.classList.add('hidden');

            users.forEach(u => {
                const tr = document.createElement('tr');
                const joinDateStr = u.joinDate ? new Date(u.joinDate).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : 'Today';
                const safeName = (u.fullName || u.username).replace(/'/g, "\\'");

                tr.innerHTML = `
                    <td>
                        <div class="applicant-identity">
                            <span class="applicant-name"><i data-lucide="user" style="width:13px;"></i> ${u.fullName || u.username}</span>
                            <span class="applicant-username">@${u.username}</span>
                        </div>
                    </td>
                    <td>${u.email}</td>
                    <td><span class="badge-growth-chip">${u.academicYear || '1st Year'}</span></td>
                    <td class="applicant-bio-cell" title="${u.bio || 'No bio provided'}">${u.bio || 'Aspiring club member'}</td>
                    <td class="text-muted text-xs">${joinDateStr}</td>
                    <td>
                        <div class="governance-actions-cell">
                            <button type="button" class="btn-approve-applicant" onclick="approveApplicant(${u.id}, '${safeName}')">
                                <i data-lucide="check"></i> Approve
                            </button>
                            <button type="button" class="btn-remove-applicant" onclick="removeApplicant(${u.id}, '${safeName}')">
                                <i data-lucide="trash-2"></i> Remove
                            </button>
                        </div>
                    </td>
                `;
                list.appendChild(tr);
            });

            if (window.lucide) window.lucide.createIcons();
        })
        .catch(err => {
            console.error("Error loading pending members:", err);
        });
}

function approveApplicant(userId, name) {
    if (!currentUser) return;
    const adminUser = currentUser.username;

    fetch(`${API_BASE}/users/${userId}/approve?adminUsername=${encodeURIComponent(adminUser)}`, {
        method: 'POST'
    })
    .then(res => {
        if (!res.ok) throw new Error("Approval failed");
        return res.json();
    })
    .then(data => {
        alert(`✓ Member Approved!\n\n${name} has been approved as an active club member.`);
        loadPendingMembers();
        refreshSecurityLogs();
    })
    .catch(err => {
        console.error(err);
        alert("Error approving member. Please try again.");
    });
}

function removeApplicant(userId, name) {
    if (!currentUser) return;
    if (!confirm(`Are you sure you want to reject & remove the membership application for ${name}?`)) {
        return;
    }

    const adminUser = currentUser.username;

    fetch(`${API_BASE}/users/${userId}/reject?adminUsername=${encodeURIComponent(adminUser)}`, {
        method: 'POST'
    })
    .then(res => {
        if (!res.ok) throw new Error("Removal failed");
        return res.json();
    })
    .then(data => {
        alert(`Application for ${name} removed.`);
        loadPendingMembers();
        refreshSecurityLogs();
    })
    .catch(err => {
        console.error(err);
        alert("Error removing application. Please try again.");
    });
}

window.approveApplicant = approveApplicant;
window.removeApplicant = removeApplicant;
window.showSignupModal = showSignupModal;
window.hideSignupModal = hideSignupModal;
window.loadPendingMembers = loadPendingMembers;



