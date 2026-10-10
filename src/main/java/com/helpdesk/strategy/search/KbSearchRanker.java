package com.helpdesk.strategy.search;

import com.helpdesk.entity.KbArticle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy pattern (F4) - Context role.
 * Holds the strategy selected by app.kb.ranking (default "weighted") and delegates scoring to it.
 * An unknown name falls back to the default so a typo in the config can never break search.
 */
@Component
public class KbSearchRanker {

    private static final String DEFAULT = "weighted";

    private final Map<String, KbRankingStrategy> strategies = new HashMap<>();
    private final KbRankingStrategy active;

    public KbSearchRanker(List<KbRankingStrategy> available,
                          @Value("${app.kb.ranking:" + DEFAULT + "}") String selected) {
        for (KbRankingStrategy s : available) {
            strategies.put(s.name(), s);
        }
        this.active = strategies.getOrDefault(selected == null ? DEFAULT : selected.trim(), strategies.get(DEFAULT));
    }

    public int score(KbArticle article, SearchTerms terms) {
        return active.score(article, terms);
    }
}
