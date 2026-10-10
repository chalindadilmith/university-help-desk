package com.helpdesk.strategy.search;

import com.helpdesk.entity.KbArticle;
import org.springframework.stereotype.Component;

/** ConcreteStrategy 2: only titles count - stricter, fewer but more precise results. */
@Component
public class TitleOnlyRankingStrategy implements KbRankingStrategy {

    @Override
    public String name() {
        return "title-only";
    }

    @Override
    public int score(KbArticle a, SearchTerms terms) {
        String title = a.getTitle().toLowerCase();
        int score = title.contains(terms.phrase()) ? 20 : 0;
        for (String w : terms.words()) {
            if (title.contains(w)) score += 5;
        }
        return score;
    }
}
