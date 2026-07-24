package com.linkpocket.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_session_id", nullable = false)
    private DeviceSession deviceSession;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID id, DeviceSession deviceSession, String tokenHash, Instant createdAt) {
        this.id = id;
        this.deviceSession = deviceSession;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    public DeviceSession getDeviceSession() {
        return deviceSession;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void consume(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }
}
