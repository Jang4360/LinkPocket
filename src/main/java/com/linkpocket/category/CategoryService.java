package com.linkpocket.category;

import com.linkpocket.common.error.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    public static final String UNCATEGORIZED_NAME = "카테고리 없음";

    private final JdbcTemplate jdbcTemplate;

    public CategoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public UUID ensureUncategorized(UUID userId) {
        List<UUID> existing = jdbcTemplate.query(
                "select id from category where user_id = ? and is_system = true",
                (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                userId
        );
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }

        jdbcTemplate.update(
                """
                        insert into category (id, user_id, name, is_system, created_at)
                        values (gen_random_uuid(), ?, ?, true, now())
                        on conflict do nothing
                        """,
                userId, UNCATEGORIZED_NAME
        );

        return jdbcTemplate.query(
                        "select id from category where user_id = ? and is_system = true",
                        (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
                        userId
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("시스템 카테고리를 만들 수 없습니다."));
    }

    @Transactional
    public CategoryResponse create(UUID userId, String name) {
        ensureUncategorized(userId);
        try {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                    "insert into category (id, user_id, name, is_system, created_at) values (?, ?, ?, false, now())",
                    id, userId, name
            );
            return new CategoryResponse(id, name, false);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(CategoryErrorCode.CATEGORY_DUPLICATE_NAME);
        }
    }

    @Transactional
    public List<CategoryResponse> list(UUID userId) {
        ensureUncategorized(userId);
        return jdbcTemplate.query(
                "select id, name, is_system from category where user_id = ? order by is_system desc, created_at",
                (resultSet, rowNum) -> new CategoryResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getBoolean("is_system")
                ),
                userId
        );
    }

    @Transactional
    public CategoryResponse rename(UUID userId, UUID categoryId, String name) {
        CategoryResponse category = ownedCategory(userId, categoryId);
        if (category.isSystem()) {
            throw new DomainException(CategoryErrorCode.CATEGORY_SYSTEM_PROTECTED);
        }
        try {
            jdbcTemplate.update(
                    "update category set name = ? where id = ? and user_id = ?",
                    name, categoryId, userId
            );
            return new CategoryResponse(categoryId, name, false);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException(CategoryErrorCode.CATEGORY_DUPLICATE_NAME);
        }
    }

    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        CategoryResponse category = ownedCategory(userId, categoryId);
        if (category.isSystem()) {
            throw new DomainException(CategoryErrorCode.CATEGORY_SYSTEM_PROTECTED);
        }
        UUID uncategorizedId = ensureUncategorized(userId);
        List<UUID> affectedLinkIds = jdbcTemplate.query(
                "select link_id from link_category where category_id = ?",
                (resultSet, rowNum) -> resultSet.getObject("link_id", UUID.class),
                categoryId
        );

        jdbcTemplate.update("delete from category where id = ? and user_id = ?", categoryId, userId);

        for (UUID linkId : affectedLinkIds) {
            Integer remainingCategories = jdbcTemplate.queryForObject(
                    "select count(*) from link_category where link_id = ?",
                    Integer.class,
                    linkId
            );
            if (remainingCategories != null && remainingCategories == 0) {
                jdbcTemplate.update(
                        "insert into link_category (link_id, category_id) values (?, ?) on conflict do nothing",
                        linkId, uncategorizedId
                );
            }
        }
    }

    public CategoryResponse ownedCategory(UUID userId, UUID categoryId) {
        return jdbcTemplate.query(
                        "select id, name, is_system from category where id = ? and user_id = ?",
                        (resultSet, rowNum) -> new CategoryResponse(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("name"),
                                resultSet.getBoolean("is_system")
                        ),
                        categoryId, userId
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
    }
}
