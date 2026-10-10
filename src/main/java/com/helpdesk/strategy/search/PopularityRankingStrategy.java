package com.helpdesk.strategy.search;

import com.helpdesk.entity.KbArticle;
import org.springframework.stereotype.Component;

/** ConcreteStrategy 3: keyword score plus a bonus for articles many students already read. */
@Component
public class PopularityRankingStrategy implements KbRankingStrategy {

    private static final int MAX_BONUS = 10;

    private final WeightedKeywordRankingStrategy keyword = new WeightedKeywordRankingStrategy();

    @Override
    public String name() {
        return "popular";
    }

    @Override
    public int score(KbArticle a, SearchTerms terms) {
        int base = keyword.score(a, terms);
        if (base == 0) {
            return 0; // popularity alone never makes an article a match
        }
        return base + Math.min(MAX_BONUS, a.getViewCount() / 10);
    }
}
