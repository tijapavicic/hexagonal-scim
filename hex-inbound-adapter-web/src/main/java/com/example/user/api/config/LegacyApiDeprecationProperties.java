package com.example.user.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration for the legacy {@code /api/users} deprecation response headers.
 * <pre>
 * api.legacy.deprecation-value – value of the {@code Deprecation} header
 * api.legacy.sunset-date       – value of the {@code Sunset} header (RFC 7231 date)
 * api.legacy.successor-link    – value of the {@code Link} header pointing to v1
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "api.legacy")
public class LegacyApiDeprecationProperties {
    private String deprecationValue = "true";
    private String sunsetDate = "Wed, 31 Dec 2026 23:59:59 GMT";
    private String successorLink = "</api/v1/users>; rel=\"successor-version\"";

    public String getDeprecationValue() {
        return deprecationValue;
    }

    public void setDeprecationValue(String deprecationValue) {
        this.deprecationValue = deprecationValue;
    }

    public String getSunsetDate() {
        return sunsetDate;
    }

    public void setSunsetDate(String sunsetDate) {
        this.sunsetDate = sunsetDate;
    }

    public String getSuccessorLink() {
        return successorLink;
    }

    public void setSuccessorLink(String successorLink) {
        this.successorLink = successorLink;
    }
}

