package com.helpdesk.strategy.search;

import com.helpdesk.entity.KbArticle;
import org.springframework.stereotype.Component;

/**
 * ConcreteStrategy 1 (default): the original ranking, moved here unchanged.
 * Whole phrase in title > words in title > words in content.
 */
@Component
public class WeightedKeywordRankingStrategy implements KbRankingStrategy {

    @Override
    public String name() {
        return "weighted";
    }

    @Override
    public int score(KbArticle a, SearchTerms terms) {
        String title = a.getTitle().toLowerCase();
        String content = a.getContent().toLowerCase();
        int score = 0;
        if (title.contains(terms.phrase())) score += 20;
        else if (content.contains(terms.phrase())) score += 6;
        for (String w : terms.words()) {
            if (title.contains(w)) score += 5;
            if (content.contains(w)) score += 1;
        }
        return score;
    }
}
