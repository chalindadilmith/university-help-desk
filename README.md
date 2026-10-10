# University Help Desk System

Web-Based Help Desk System for University Students — SE2030 Group Project (MLB-WEB1G1-08).

A full-stack Spring Boot MVC application covering all six documented modules (F1–F6), with session-based Spring Security authentication, Microsoft SQL Server persistence via JPA/Hibernate, and a Thymeleaf UI built on a shared design system.

## Tech stack
Java 21 · Spring Boot 3.3 · Spring MVC · Thymeleaf · Spring Security + BCrypt · Spring Data JPA / Hibernate · SQL Server · Maven.

## Prerequisites
- JDK 21
- Maven 3.9+ (or use your IDE's built-in Maven)
- Microsoft SQL Server (Express is fine) running locally or reachable over the network

## Local setup (every team member runs this on their own machine)

1. Install SQL Server and make sure it is running. Create an empty database named `helpdesk_db` and a login the app can use (`CREATE DATABASE helpdesk_db;`). The app creates and updates its own tables on startup.
2. Set your local DB credentials as environment variables (do **not** hard-code real credentials into `application.properties`):
   ```bash
   export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=helpdesk_db;encrypt=true;trustServerCertificate=true"
   export DB_USERNAME=helpdesk_user
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

## Email setup (forgot-password links)

The app sends mail from ONE Gmail account (any recipient address works, Gmail or university).

1. Create or pick a Gmail account for the system, e.g. `helpdesk.yourproject@gmail.com`.
2. Turn on 2-Step Verification, then create an App Password at https://myaccount.google.com/apppasswords (16 characters).
3. Set environment variables before starting the app (never commit them):
   ```powershell
   $env:MAIL_USERNAME="helpdesk.yourproject@gmail.com"
   $env:MAIL_PASSWORD="abcdefghijklmnop"      # the App Password, no spaces
   $env:APP_BASE_URL="http://localhost:8080"  # public URL used inside the emailed link
   ```
4. Run `mvn spring-boot:run`, open `/forgot-password`, enter a registered student's email, and check the inbox (and spam folder).

Without `MAIL_USERNAME` the reset link is printed in the application log instead, which is enough for development.

## Test accounts (seeded automatically on first run)

| Role | Email | Password |
|---|---|---|
| Admin | admin@helpdesk.university | Admin@123 |
| Staff | staff@helpdesk.university | Staff@123 |
| Student | *(register your own at `/register`)* | — |

This is a **dev-only convenience** (`DemoDataSeeder`). Set `SEED_DEMO_DATA=false` once your team has entered real accounts/data, and before final submission/demo day if you want a clean slate.

Also seeded on first run (all editable/removable afterwards through the normal admin screens — this exists purely so the app isn't empty the first time you open it): one "General Support" staff work-pool with three ticket categories (IT, Academic, Facilities) and routing rules for each priority; three sample Knowledge Base articles; one open semester ("2026 Semester 2") with two sample course offerings.

### Starting from a completely fresh database

To wipe every table (tickets, users, audit log, uploads...) and get a clean site with only the admin and staff accounts above, start the app **once** with the reset switch on, then restart it normally:

```
RESET_DATABASE=true mvn spring-boot:run      # Windows PowerShell: $env:RESET_DATABASE="true"; mvn spring-boot:run
```

**Stop the app and restart it without `RESET_DATABASE`, or the data will be wiped again on every start.** `database/reset-fresh-database.sql` does the same wipe by hand. Both admin and staff must change their password at first login. Admin accounts cannot be created from the UI; staff are created (and put into work pools) on the Staff Work Pools page.

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
Admin sets up semesters (open/close registration) and degree specialisations (`/admin/registration-config`), and adds, edits and deletes modules and the modules offered in each semester (`/admin/modules`). Every student gets a unique system-generated Student ID (e.g. `STU202600012`). Students run an **eligibility check** (active account, student ID, registration window, not already approved, modules offered), then fill in the **registration form** — degree specialisation, academic year, semester of study, contact number and modules — at `/student/registration`. Only modules for the chosen specialisation/year can be picked; seat capacity and the 18-credit limit are enforced server-side. Each submission gets a unique registration number (e.g. `SR2026-000045`) and stays **PENDING until an Admin approves or rejects it** at `/admin/registrations` (students and admins are notified). **No payment step** — removed from scope in Phase 0 (see the project's `phase0-requirements-analysis.md`).
Database changes are applied automatically on startup (Hibernate `ddl-auto=update` + `config/RegistrationSchemaInitializer`); the same changes are in `database/semester-registration-migration.sql` for running manually in SSMS.

## Design pattern: Observer (SE2030 requirement)

Ticket events (created, assigned, status changed, replied, released, transferred, escalated, reopened) are announced through the **Observer** pattern instead of `TicketServiceImpl` calling the notification, e-mail and audit services itself.

| Lecture role | Class (`com.helpdesk.observer`) |
|---|---|
| Subject | `TicketSubject` |
| ConcreteSubject | `TicketEventSubject` (Spring injects all observers, sorted by `@Order`) |
| Observer | `TicketObserver` |
| ConcreteObservers | `AuditLogObserver` (1), `StaffNotificationObserver` (2), `StudentNotificationObserver` (3) |
| Client | `TicketServiceImpl` (calls `ticketEvents.notifyObservers(...)`) |

To add a new reaction (e.g. SMS on escalation) write one new `@Component` implementing `TicketObserver`; `TicketServiceImpl` does not change.
Diagrams and a runnable plain-Java demo (`java ObserverDemo.java`) are in `docs/design-pattern/`; unit tests are in `src/test/java/com/helpdesk/observer/`.

## Documented simplifications (intentional, not bugs)

- **Email is only used for password reset and Lost & Found verification, and there is no SMS.** When an Admin creates a Staff/Admin account, the temporary password is shown once on screen instead of emailed. If `MAIL_USERNAME` is not set, emails are written to the application log instead of being sent (see "Email setup").
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
