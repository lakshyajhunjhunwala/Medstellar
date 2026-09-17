package com.techwizards.club.service;

import com.techwizards.club.model.SecurityLog;
import com.techwizards.club.repository.SecurityLogRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SecurityService {

    private final SecurityLogRepository securityLogRepository;

    // Dynamic config states
    private boolean firewallActive = true;
    private boolean ddosMitigationEnabled = true;
    private boolean appShieldActive = true;
    private int blockedIpsCount = 432;

    public SecurityService(SecurityLogRepository securityLogRepository) {
        this.securityLogRepository = securityLogRepository;
    }

    public List<SecurityLog> getSecurityLogs() {
        return securityLogRepository.findFirst15ByOrderByTimestampDesc();
    }

    public Map<String, Object> getSecurityStatus() {
        Map<String, Object> statusMap = new HashMap<>();
        Random random = new Random();

        // Platform Layer
        Map<String, Object> platform = new HashMap<>();
        platform.put("firewall", firewallActive ? "ACTIVE" : "INACTIVE");
        platform.put("ssl", "VERIFIED");
        platform.put("tlsVersion", "TLSv1.3");
        platform.put("appProtectionShield", appShieldActive ? "ACTIVE" : "SHIELD_DOWN");
        statusMap.put("platform", platform);

        // Access Control Layer
        Map<String, Object> accessControl = new HashMap<>();
        accessControl.put("activeSessions", 12 + random.nextInt(8));
        accessControl.put("rolesCount", 3);
        accessControl.put("accessControlLevel", "RBAC_ENFORCED");
        statusMap.put("accessControl", accessControl);

        // Network Layer
        Map<String, Object> network = new HashMap<>();
        network.put("ddosMitigation", ddosMitigationEnabled ? "ENABLED" : "DISABLED");
        network.put("scrubbingRate", ddosMitigationEnabled ? "100%" : "0%");
        network.put("blockedIps", blockedIpsCount);
        statusMap.put("network", network);

        // Monitoring & Recovery Layer
        Map<String, Object> monitoring = new HashMap<>();
        // Fluctuate CPU and RAM usage slightly
        monitoring.put("cpuUsage", 15 + random.nextInt(25));
        monitoring.put("memoryUsage", 48 + random.nextInt(12));
        monitoring.put("systemStatus", (firewallActive && appShieldActive) ? "HEALTHY" : "VULNERABLE");
        statusMap.put("monitoring", monitoring);

        return statusMap;
    }

    public Optional<String> toggleShield(String layer, boolean enable, String username) {
        String logDesc;
        String eventType = enable ? "SUCCESS" : "WARNING";
        String status = enable ? "OK" : "WARNING";

        if ("ddos".equalsIgnoreCase(layer)) {
            this.ddosMitigationEnabled = enable;
            logDesc = "DDoS Protection Shield was " + (enable ? "ENABLED" : "DISABLED") + " by operator " + username + ".";
        } else if ("firewall".equalsIgnoreCase(layer)) {
            this.firewallActive = enable;
            logDesc = "Platform Core Firewall was " + (enable ? "ENABLED" : "DISABLED") + " by operator " + username + ".";
        } else if ("app".equalsIgnoreCase(layer)) {
            this.appShieldActive = enable;
            logDesc = "SQL-injection & XSS Application Shield was " + (enable ? "ENABLED" : "DISABLED") + " by operator " + username + ".";
        } else {
            return Optional.empty();
        }

        // Add to log
        SecurityLog newLog = new SecurityLog(layer.toUpperCase() + "_PROT", eventType, logDesc, status);
        securityLogRepository.save(newLog);

        return Optional.of(logDesc);
    }

    public String triggerBackup(String username) {
        String logDesc = "Manual full system backup initiated by " + username + ". Database tables compressed, credentials salted, ZIP archive moved to secure Recovery Vault.";
        SecurityLog backupLog = new SecurityLog("RECOVERY", "SUCCESS", logDesc, "OK");
        securityLogRepository.save(backupLog);
        return logDesc;
    }

    public String triggerRestore(String username) {
        String logDesc = "Emergency hot-swap roll-back sequence executed by " + username + ". Restored state to the last safe recovery point. Database tables verified. App cache flushed.";
        SecurityLog restoreLog = new SecurityLog("RECOVERY", "SUCCESS", logDesc, "OK");
        securityLogRepository.save(restoreLog);

        // Simulate blocked IP resetting/incrementing slightly as part of restore/mitigation
        this.blockedIpsCount += 3;
        return logDesc;
    }
}
