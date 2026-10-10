package com.helpdesk.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of the semester-registration eligibility check - a list of named
 * checks, each passed or failed with a short explanation, shown to the
 * student before the registration form.
 */
public class EligibilityResult {

    /** Plain class (not a record) so Thymeleaf/SpEL can read it as c.label / c.passed / c.detail. */
    public static class Check {
        private final String label;
        private final boolean passed;
        private final String detail;

        public Check(String label, boolean passed, String detail) {
            this.label = label;
            this.passed = passed;
            this.detail = detail;
        }

        public String getLabel() {
            return label;
        }

        public boolean isPassed() {
            return passed;
        }

        public String getDetail() {
            return detail;
        }
    }

    private final List<Check> checks = new ArrayList<>();

    public EligibilityResult add(String label, boolean passed, String detail) {
        checks.add(new Check(label, passed, detail));
        return this;
    }

    public List<Check> getChecks() {
        return Collections.unmodifiableList(checks);
    }

    public boolean isEligible() {
        return checks.stream().allMatch(Check::isPassed);
    }

    /** First failing reason, used as the error message when a submit is blocked. */
    public String getFirstFailure() {
        return checks.stream().filter(c -> !c.isPassed()).map(Check::getDetail).findFirst().orElse(null);
    }
}
