import java.util.*;

/**
 * Standalone demo of the Strategy pattern as used in the Help Desk (no Spring, no database).
 * Run:  javac StrategyDemo.java && java StrategyDemo
 * It mirrors the real classes in com.helpdesk.strategy.* using simplified data.
 */
public class StrategyDemo {

    // ---------- 1. Report export (F5): Strategy + ConcreteStrategies + Context ----------
    record Ticket(String ref, String subject, String status) {}

    interface ReportExportStrategy {                       // Strategy
        String format();
        String export(List<Ticket> tickets);
    }
    static class Csv implements ReportExportStrategy {     // ConcreteStrategy
        public String format() { return "csv"; }
        public String export(List<Ticket> t) {
            StringBuilder sb = new StringBuilder("Reference,Subject,Status\n");
            t.forEach(x -> sb.append(x.ref()).append(',').append(x.subject()).append(',').append(x.status()).append('\n'));
            return sb.toString();
        }
    }
    static class Json implements ReportExportStrategy {    // ConcreteStrategy
        public String format() { return "json"; }
        public String export(List<Ticket> t) {
            StringJoiner j = new StringJoiner(",\n", "[\n", "\n]");
            t.forEach(x -> j.add("  {\"ref\":\"" + x.ref() + "\",\"subject\":\"" + x.subject() + "\",\"status\":\"" + x.status() + "\"}"));
            return j.toString();
        }
    }
    static class ReportExporter {                          // Context
        private final Map<String, ReportExportStrategy> byFormat = new LinkedHashMap<>();
        ReportExporter(List<ReportExportStrategy> all) { all.forEach(s -> byFormat.put(s.format(), s)); }
        String export(String format, List<Ticket> tickets) {
            ReportExportStrategy s = byFormat.get(format);
            if (s == null) throw new IllegalArgumentException("Unsupported format " + format + ", use " + byFormat.keySet());
            return s.export(tickets);
        }
    }

    // ---------- 2. Ticket routing (F3): first strategy that answers wins ----------
    interface TicketRoutingStrategy { Optional<String> route(String category, String priority); }
    static class RuleBased implements TicketRoutingStrategy {
        private final Map<String, String> rules = Map.of("Network/HIGH", "Urgent IT Pool");
        public Optional<String> route(String c, String p) { return Optional.ofNullable(rules.get(c + "/" + p)); }
    }
    static class CategoryDefault implements TicketRoutingStrategy {
        private final Map<String, String> defaults = Map.of("Network", "IT Pool", "Fees", "Finance Pool");
        public Optional<String> route(String c, String p) { return Optional.ofNullable(defaults.get(c)); }
    }
    static class TicketRouter {
        private final List<TicketRoutingStrategy> strategies;
        TicketRouter(List<TicketRoutingStrategy> s) { strategies = s; }
        String route(String c, String p) {
            for (TicketRoutingStrategy s : strategies) {
                Optional<String> pool = s.route(c, p);
                if (pool.isPresent()) return pool.get();
            }
            return "(unrouted)";
        }
    }

    // ---------- 3. Registration rules (F6): every rule runs in order ----------
    interface RegistrationRule { void validate(int credits, boolean clash); }
    static class CreditLimit implements RegistrationRule {
        public void validate(int credits, boolean clash) {
            if (credits > 18) throw new IllegalStateException(credits + " credits exceeds the 18-credit limit");
        }
    }
    static class ScheduleClash implements RegistrationRule {
        public void validate(int credits, boolean clash) {
            if (clash) throw new IllegalStateException("two modules overlap");
        }
    }
    static class RegistrationValidator {
        private final List<RegistrationRule> rules;
        RegistrationValidator(List<RegistrationRule> r) { rules = r; }
        String check(int credits, boolean clash) {
            try { rules.forEach(r -> r.validate(credits, clash)); return "OK"; }
            catch (IllegalStateException e) { return "REJECTED: " + e.getMessage(); }
        }
    }

    public static void main(String[] args) {
        List<Ticket> tickets = List.of(new Ticket("TCK-000001", "WiFi down", "OPEN"), new Ticket("TCK-000002", "Fee query", "RESOLVED"));
        ReportExporter exporter = new ReportExporter(List.of(new Csv(), new Json()));
        System.out.println("== Export as csv ==\n" + exporter.export("csv", tickets));
        System.out.println("== Export as json ==\n" + exporter.export("json", tickets) + "\n");

        TicketRouter router = new TicketRouter(List.of(new RuleBased(), new CategoryDefault()));
        System.out.println("Network/HIGH   -> " + router.route("Network", "HIGH"));
        System.out.println("Network/LOW    -> " + router.route("Network", "LOW"));
        System.out.println("Library/MEDIUM -> " + router.route("Library", "MEDIUM") + "\n");

        RegistrationValidator v = new RegistrationValidator(List.of(new ScheduleClash(), new CreditLimit()));
        System.out.println("16 credits, no clash -> " + v.check(16, false));
        System.out.println("21 credits, no clash -> " + v.check(21, false));
        System.out.println("12 credits, clash    -> " + v.check(12, true));
    }
}
