package com.linkpocket.link;

import com.linkpocket.category.CategoryErrorCode;
import com.linkpocket.category.CategoryResponse;
import com.linkpocket.category.CategoryService;
import com.linkpocket.common.error.DomainException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class LinkCategoryService {

    private final JdbcTemplate jdbcTemplate;
    private final CategoryService categoryService;

    public LinkCategoryService(JdbcTemplate jdbcTemplate, CategoryService categoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.categoryService = categoryService;
    }

    @Transactional
    public UUID ensureUncategorized(UUID userId) {
        return categoryService.ensureUncategorized(userId);
    }

    @Transactional
    public void ensureDefaultAssignment(UUID linkId, UUID uncategorizedId) {
        Integer categoryCount = jdbcTemplate.queryForObject(
                "select count(*) from link_category where link_id = ?",
                Integer.class,
                linkId
        );
        if (categoryCount != null && categoryCount > 0) {
            return;
        }
        jdbcTemplate.update(
                "insert into link_category (link_id, category_id) values (?, ?) on conflict do nothing",
                linkId, uncategorizedId
        );
    }

    @Transactional
    public void replaceCategories(UUID userId, UUID linkId, List<UUID> categoryIds) {
        ensureOwnedLink(userId, linkId);
        UUID uncategorizedId = categoryService.ensureUncategorized(userId);
        Set<UUID> requestedIds = categoryIds == null ? Set.of() : new LinkedHashSet<>(categoryIds);
        Set<UUID> realCategoryIds = new LinkedHashSet<>();

        for (UUID categoryId : requestedIds) {
            CategoryResponse category = categoryService.ownedCategory(userId, categoryId);
            if (!category.isSystem()) {
                realCategoryIds.add(categoryId);
            }
        }

        jdbcTemplate.update("delete from link_category where link_id = ?", linkId);
        if (realCategoryIds.isEmpty()) {
            jdbcTemplate.update(
                    "insert into link_category (link_id, category_id) values (?, ?) on conflict do nothing",
                    linkId, uncategorizedId
            );
            return;
        }
        for (UUID categoryId : realCategoryIds) {
            jdbcTemplate.update(
                    "insert into link_category (link_id, category_id) values (?, ?) on conflict do nothing",
                    linkId, categoryId
            );
        }
    }

    private void ensureOwnedLink(UUID userId, UUID linkId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from link where id = ? and user_id = ?",
                Integer.class,
                linkId, userId
        );
        if (count == null || count == 0) {
            throw new DomainException(LinkErrorCode.LINK_NOT_FOUND);
        }
    }
}
