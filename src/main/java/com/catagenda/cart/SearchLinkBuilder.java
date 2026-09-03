package com.catagenda.cart;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Pure, stateless helper (T4.1, resolved as a Tech Lead call, no ADR — see
 * the Phase 4 tech-task doc's Section 0): builds a generic web-search URL
 * from an item's display name. No retailer integration, no API keys, no
 * persisted state — satisfies P4-3's "search assistance only, not store
 * integration" AC directly. Deliberately has no Spring dependencies so it's
 * trivially unit-testable with plain JUnit, and a one-line, easily-swapped
 * template if the search destination ever needs to change.
 */
public final class SearchLinkBuilder {

    private static final String SEARCH_URL_TEMPLATE = "https://www.google.com/search?q=%s";

    private SearchLinkBuilder() {
    }

    public static String buildSearchUrl(String displayName) {
        String query = displayName + " for cats";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return String.format(SEARCH_URL_TEMPLATE, encodedQuery);
    }
}
