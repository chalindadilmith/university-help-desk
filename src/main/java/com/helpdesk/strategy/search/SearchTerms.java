package com.helpdesk.strategy.search;

import java.util.Set;

/** The cleaned-up search query: the whole lower-case phrase plus its meaningful words. */
public record SearchTerms(String phrase, Set<String> words) {
}
