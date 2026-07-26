package com.linkpocket.link;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class LinkService {

    private final JdbcTemplate jdbcTemplate;
    private final CanonicalUrlNormalizer canonicalUrlNormalizer = new CanonicalUrlNormalizer();

    public LinkService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * PostgreSQL의 unique constraint와 ON CONFLICT가 같은 URL의 동시 저장을 한 row로 수렴시킨다.
     * 충돌 시에도 no-op UPDATE와 RETURNING으로 신규/기존을 같은 경로로 반환한다.
     * xmax는 INSERT된 row에서만 0이므로 별도 조회 없이 신규 여부도 함께 판정한다.
     */
    @Transactional
    public LinkSaveResponse save(UUID userId, String rawUrl) {
        String canonicalUrl = canonicalUrlNormalizer.normalize(rawUrl);
        return jdbcTemplate.queryForObject(
                """
                        insert into link (id, user_id, url, canonical_url, status, created_at)
                        values (gen_random_uuid(), ?, ?, ?, 'PENDING', now())
                        on conflict (user_id, canonical_url)
                        do update set url = link.url
                        returning id, canonical_url, status, (xmax = 0) as inserted
                        """,
                (resultSet, rowNum) -> new LinkSaveResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("canonical_url"),
                        resultSet.getString("status"),
                        !resultSet.getBoolean("inserted")
                ),
                userId, rawUrl, canonicalUrl
        );
    }

    @Transactional(readOnly = true)
    public LinkLookupResponse lookup(UUID userId, String rawUrl) {
        String canonicalUrl = canonicalUrlNormalizer.normalize(rawUrl);
        Optional<UUID> linkId = jdbcTemplate.query(
                        "select id from link where user_id = ? and canonical_url = ?",
                        (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                        userId, canonicalUrl
                )
                .stream()
                .findFirst();
        return new LinkLookupResponse(linkId.isPresent(), linkId.orElse(null));
    }

    @Transactional
    public void delete(UUID userId, UUID linkId) {
        int deleted = jdbcTemplate.update(
                "delete from link where id = ? and user_id = ?",
                linkId, userId
        );
        if (deleted == 0) {
            throw new com.linkpocket.common.error.DomainException(LinkErrorCode.LINK_NOT_FOUND);
        }
    }
}
