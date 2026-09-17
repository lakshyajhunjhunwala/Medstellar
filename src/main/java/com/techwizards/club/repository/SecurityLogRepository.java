package com.techwizards.club.repository;

import com.techwizards.club.model.SecurityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityLogRepository extends JpaRepository<SecurityLog, Long> {
    List<SecurityLog> findFirst15ByOrderByTimestampDesc();
}
