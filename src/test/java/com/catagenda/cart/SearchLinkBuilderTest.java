package com.catagenda.cart;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** T4.2 — plain JUnit, no Spring context needed: {@link SearchLinkBuilder} has no Spring dependencies. */
class SearchLinkBuilderTest {

    @Test
    void buildsAWellFormedUrlForASimpleName() {
        String url = SearchLinkBuilder.buildSearchUrl("Litter");

        assertThat(url).startsWith("https://www.google.com/search?q=");
        assertThat(url).contains("Litter");
        assertThatCode(() -> URI.create(url).toURL()).doesNotThrowAnyException();
    }

    @Test
    void encodesSpacesAndPunctuationInAFreeTextName() {
        String url = SearchLinkBuilder.buildSearchUrl("Flea/Tick Prevention & Brush (extra soft)");
        String query = url.substring(url.indexOf("q=") + 2);

        // The query string itself must not contain a raw space, slash,
        // ampersand or parenthesis — each is percent- (or "+"-) encoded so
        // the URL stays well-formed regardless of what free text a manually
        // added item's name contains.
        assertThat(query).doesNotContain(" ", "/", "&", "(", ")");
        assertThat(query).contains("%2F").contains("%26").contains("%28").contains("%29");
        assertThatCode(() -> URI.create(url).toURL()).doesNotThrowAnyException();
    }

    @Test
    void appendsTheForCatsQualifierToTheSearchQuery() {
        String url = SearchLinkBuilder.buildSearchUrl("Litter");

        assertThat(url).contains("for+cats");
    }
}
