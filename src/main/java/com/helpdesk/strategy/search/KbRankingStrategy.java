package com.helpdesk.strategy.search;

import com.helpdesk.entity.KbArticle;

/**
 * Strategy pattern (F4 Knowledge Base) - Strategy role.
 * One way of scoring how well an article matches a search. Score 0 means "not a match";
 * higher scores are listed first.
 */
public interface KbRankingStrategy {

    /** Name used in application.properties: app.kb.ranking=weighted | title-only | popular */
    String name();

    int score(KbArticle article, SearchTerms terms);
}
