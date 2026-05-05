package com.example.user.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable defaults for the paginated GET-all endpoint.
 * <pre>
 * api.pagination.default-page     – zero-indexed first page (default: 0)
 * api.pagination.default-size     – items per page (default: 10)
 * api.pagination.default-pageable – whether pagination is on by default (default: true)
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "api.pagination")
public class ApiPaginationProperties {

    private int defaultPage = 0;
    private int defaultSize = 10;
    private boolean defaultPageable = true;

    public int getDefaultPage() {
        return defaultPage;
    }

    public void setDefaultPage(int defaultPage) {
        this.defaultPage = defaultPage;
    }

    public int getDefaultSize() {
        return defaultSize;
    }

    public void setDefaultSize(int defaultSize) {
        this.defaultSize = defaultSize;
    }

    public boolean isDefaultPageable() {
        return defaultPageable;
    }

    public void setDefaultPageable(boolean defaultPageable) {
        this.defaultPageable = defaultPageable;
    }
}

