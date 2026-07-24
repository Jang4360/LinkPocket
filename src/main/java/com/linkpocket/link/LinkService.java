package com.linkpocket.link;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                        returning id, canonical_url, status
                        """,
                (resultSet, rowNum) -> new LinkSaveResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("canonical_url"),
                        resultSet.getString("status")
                ),
                userId, rawUrl, canonicalUrl
        );
    }
}
