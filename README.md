# University Help Desk System

Web-Based Help Desk System for University Students — SE2030 Group Project (MLB-WEB1G1-08).

A full-stack Spring Boot MVC application covering all six documented modules (F1–F6), with session-based Spring Security authentication, MySQL persistence via JPA/Hibernate, and a Thymeleaf UI built on a shared design system.

## Tech stack
Java 21 · Spring Boot 3.3 · Spring MVC · Thymeleaf · Spring Security + BCrypt · Spring Data JPA / Hibernate · MySQL · Maven.

## Prerequisites
- JDK 21
- Maven 3.9+ (or use your IDE's built-in Maven)
- MySQL 8.x running locally (or reachable over the network)

## Local setup (every team member runs this on their own machine)

1. Install MySQL and make sure it's running. Create the schema, or let the app create it for you (see `createDatabaseIfNotExist=true` below).
2. Set your local DB credentials as environment variables (do **not** hard-code real credentials into `application.properties`):
   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/helpdesk_db?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true"
   export DB_USERNAME=root
   export DB_PASSWORD=yourpassword
   ```
   On Windows PowerShell: `$env:DB_URL="..."`, `$env:DB_USERNAME="..."`, `$env:DB_PASSWORD="..."`.
3. Build and run:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
4. Open `http://localhost:8080` (override the port with `SERVER_PORT`).
5. On first startup the app seeds itself with demo accounts and baseline reference data (see below) so it's immediately usable — nothing further to configure.

## Test accounts (seeded automatically on first run)

| Role | Email | Password |
|---|---|---|
| Admin | admin@helpdesk.university | Admin@123 |
| Staff | staff@helpdesk.university | Staff@123 |
| Student | *(register your own at `/register`)* | — |

This is a **dev-only convenience** (`DemoDataSeeder`). Set `SEED_DEMO_DATA=false` once your team has entered real accounts/data, and before final submission/demo day if you want a clean slate.

Also seeded on first run (all editable/removable afterwards through the normal admin screens — this exists purely so the app isn't empty the first time you open it): one "General Support" staff work-pool with three ticket categories (IT, Academic, Facilities) and routing rules for each priority; three sample Knowledge Base articles; one open semester ("2026 Semester 2") with two sample course offerings.

## Feature map (what's implemented, by module)

**F1 — Account, User & Staff Management + Lost & Found**
Student self-registration (`/register`); Admin creates/deactivates/reactivates Staff and Admin accounts with a generated temporary password (`/admin/users`); every admin action is written to the audit log (`/admin/audit-log`); students report and search Lost & Found items (`/student/lost-found`), submit ownership claims, and an Admin verifies claims before hand-over (`/admin/lost-found`); every user can view/edit their own profile (`/profile`).

**F2 — Ticket Management**
Students create tickets against a category (`/student/tickets`), track status in real time, reopen a resolved ticket if unsatisfied. Staff work a queue built from tickets assigned to them plus unassigned tickets in their work-pool's categories (`/staff/tickets`), take ownership, reply, mark resolved/closed, or escalate.

**F3 — Admin Configuration (Ticket Routing)**
Admin defines ticket categories, groups them into staff work-pools, assigns staff to pools, and sets routing rules per category+priority (`/admin/config`) — a database unique constraint prevents two conflicting rules for the same category+priority combination.

**F4 — Knowledge Base + Chatbot**
Students search published FAQ articles and use a keyword-matching chatbot widget (`/student/kb`) that searches the same KB and, after two unmatched questions in a row, offers to create a ticket. Staff manage articles (`/staff/kb`).

**F5 — Notifications, Reporting & Feedback**
A notification bell (top-right on every page) fires on ticket status changes, Lost & Found claim events, and registration confirmations. Students rate resolved tickets (`student/ticket-detail.html`). Staff/Admin view live ticket-volume, resolution-time, and satisfaction statistics at `/reports`, computed from the database on demand — no fabricated numbers.

**F6 — Semester Registration**
Admin sets up semesters, courses, and course offerings (`/admin/registration-config`). Students browse open semesters, select courses, and register (`/student/registration`), with a 18-credit-per-semester limit enforced server-side. **No payment step** — removed from scope in Phase 0 (see the project's `phase0-requirements-analysis.md`).

## Documented simplifications (intentional, not bugs)

- **No real email/SMS provider.** When an Admin creates a Staff/Admin account, the temporary password is shown once on screen instead of emailed (UC-02 assumed an email step; there's no SMTP integration in scope).
- **Chatbot is keyword-matching against the KB**, not a real NLP/LLM service — appropriate for the project's time budget (documented as a Phase 0 decision).
- **No structured course schedule/timetable data**, so registration enforces a credit limit but not day/time conflict detection — `Course`/`CourseOffering` only store a free-text schedule string.
- **Reports are computed live via JPA queries/streams** each time the page loads rather than cached — fine at this project's scale, and guarantees the numbers are always current.

## Project structure
```
src/main/java/com/helpdesk/
  config/       SecurityConfig, DemoDataSeeder
  controller/   Spring MVC controllers, one family per module
  service/      Business logic interfaces + service/impl/ implementations
  repository/   Spring Data JPA repositories
  entity/       JPA entities (see phase0/phase2 design docs)
  dto/          Form-backing objects used by Thymeleaf forms
  security/     UserDetailsService + custom security classes
  exception/    Custom exceptions + global @ControllerAdvice handler
  util/         Small shared helpers
src/main/resources/
  templates/    Thymeleaf pages: fragments/ (shared layout), auth/, student/, staff/, admin/, error/, reports.html, profile.html
  static/       css/style.css (design system), js/app.js
  application.properties
```

## Status
- [x] Phase 0 — Requirements analysis (see `phase0-requirements-analysis.md` in the project)
- [x] Phase 1 — System architecture
- [x] Phase 2 — MySQL database design
- [x] Phase 3 — Spring Boot + Maven backend setup
- [x] Phase 4 — Spring Security + BCrypt authentication/authorization
- [x] Phase 5 — Core backend features (all six modules, F1–F6)
- [x] Phase 6/7 — Thymeleaf frontend + per-feature UI (shared layout/design system)
- [x] Phase 8 — Backend/frontend integration (every screen is wired to real MySQL data, no fake data)
- [x] Phase 9 — Notifications and feedback
- [~] Phase 10 — Validation and error handling (bean validation + global exception handler in place; deeper edge-case coverage still worth a pass)
- [ ] Phase 11 — Testing (not yet written — see "What's next" below)
- [ ] Phase 12 — Further UI polish / animations
- [ ] Phase 13 — Security review
- [ ] Phase 14 — GitHub integration (branch strategy, PR process)
- [ ] Phase 15 — Deployment
- [ ] Phase 16 — Final presentation prep

## What's next (given more time)
Automated tests (JUnit/Mockito/MockMvc) for each service and controller; a proper Git branch/PR workflow across the team; a security pass (rate-limiting login attempts, tightening file upload validation for ticket attachments if that's added); deployment instructions (e.g. a cloud MySQL + a simple host for the jar).

## Team (SE2030 — MLB-WEB1G1-08)
| Member | Reg. No. | Module |
|---|---|---|
| Sansukan R | IT25100501 | F1 — Account, User & Staff Management + Lost and Found |
| Weerabahu S.R | IT25100480 | F2 — Ticket Management |
| Jayatissa H.I.D | IT25100518 | F3 — Admin Configuration (Ticket Routing) |
| Dilmith A.C | IT25100476 | F4 — Knowledge Base (with Chatbot) |
| Gunarathna K.A.D.T | IT25100471 | F5 — Notification, Reporting & Feedback |
| Yakshanth N | IT25100481 | F6 — Semester Registration |
