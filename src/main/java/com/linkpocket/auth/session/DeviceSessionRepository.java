package com.linkpocket.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {
}
