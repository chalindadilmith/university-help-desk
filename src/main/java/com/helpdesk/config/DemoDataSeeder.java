package com.helpdesk.config;

import com.helpdesk.entity.*;
import com.helpdesk.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * DEV/DEMO CONVENIENCE ONLY - not a real feature.
 *
 * Two things are seeded here, for two different reasons:
 *
 * 1. Demo Admin + Staff accounts - there is (by design, per Phase 0) no
 *    public signup path for Staff/Admin accounts, only a System Admin can
 *    create them (UC-02). Without this seeder nobody could ever log in as
 *    Staff or Admin, because no Admin would exist yet to create one.
 *
 * 2. Baseline reference/config data (ticket categories, a staff work-pool,
 *    a couple of KB articles, an open semester with a course offering) -
 *    this is exactly the kind of setup a System Admin would normally do
 *    once through the UI (Ticket Routing Config / Registration Setup /
 *    Manage FAQ). It's seeded here purely so the app isn't a blank slate
 *    the first time your team runs it - everything it creates can be
 *    edited or deleted afterwards through the normal admin screens.
 *
 * Set SEED_DEMO_DATA=false once your team has entered real data, and
 * certainly before final submission/demo day.
 */
@Slf4j
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TicketCategoryRepository categoryRepository;
    private final JobPoolRepository poolRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final KbCategoryRepository kbCategoryRepository;
    private final KbArticleRepository kbArticleRepository;
    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;

    @Value("${app.seed-demo-data:true}")
    private boolean seedDemoData;

    public DemoDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           TicketCategoryRepository categoryRepository, JobPoolRepository poolRepository,
                           RoutingRuleRepository routingRuleRepository, KbCategoryRepository kbCategoryRepository,
                           KbArticleRepository kbArticleRepository, SemesterRepository semesterRepository,
                           CourseRepository courseRepository, CourseOfferingRepository offeringRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryRepository = categoryRepository;
        this.poolRepository = poolRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.kbCategoryRepository = kbCategoryRepository;
        this.kbArticleRepository = kbArticleRepository;
        this.semesterRepository = semesterRepository;
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedDemoData) {
            log.info("app.seed-demo-data=false - skipping demo data seeding.");
            return;
        }

        User admin = seedUserIfMissing("admin@helpdesk.university", "Admin@123", "Demo System Admin", "ADM-0001", Role.ADMIN);
        User staff = seedUserIfMissing("staff@helpdesk.university", "Staff@123", "Demo Help Desk Staff", "STF-0001", Role.STAFF);

        seedTicketRoutingConfig(staff);
        seedKnowledgeBase(admin);
        seedSemesterCatalog();
    }

    private User seedUserIfMissing(String email, String rawPassword, String fullName, String universityId, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = User.builder()
                    .fullName(fullName)
                    .universityId(universityId)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .status(AccountStatus.ACTIVE)
                    .build();
            User saved = userRepository.save(user);
            log.warn("Seeded DEMO {} account -> email: {} / password: {}  (DEV ONLY - change/remove before final submission)",
                    role, email, rawPassword);
            return saved;
        });
    }

    private void seedTicketRoutingConfig(User staff) {
        if (categoryRepository.count() > 0) {
            return; // already configured (either by us on a previous run, or by an Admin through the UI)
        }

        JobPool generalPool = poolRepository.save(JobPool.builder()
                .name("General Support")
                .description("Default work-pool handling IT, Academic and Facilities tickets.")
                .build());
        generalPool.getStaffMembers().add(staff);
        poolRepository.save(generalPool);

        TicketCategory it = categoryRepository.save(TicketCategory.builder().name("IT").description("Accounts, Wi-Fi, systems access").pool(generalPool).build());
        TicketCategory academic = categoryRepository.save(TicketCategory.builder().name("Academic").description("Course registration, grades, transcripts").pool(generalPool).build());
        TicketCategory facilities = categoryRepository.save(TicketCategory.builder().name("Facilities").description("Classrooms, labs, campus maintenance").pool(generalPool).build());

        for (TicketCategory category : new TicketCategory[]{it, academic, facilities}) {
            for (Priority priority : Priority.values()) {
                routingRuleRepository.save(RoutingRule.builder()
                        .category(category)
                        .priority(priority)
                        .targetPool(generalPool)
                        .build());
            }
        }
        log.info("Seeded baseline ticket categories, one staff work-pool, and routing rules.");
    }

    private void seedKnowledgeBase(User author) {
        if (kbArticleRepository.count() > 0) {
            return;
        }
        KbCategory general = kbCategoryRepository.save(KbCategory.builder().name("General").build());

        kbArticleRepository.save(KbArticle.builder()
                .title("How do I reset my password?")
                .content("Go to the Login page and click 'Forgot password' (if enabled), or contact the IT help desk by raising a ticket under the IT category with your student ID.")
                .category(general).author(author).status(KbArticleStatus.PUBLISHED).viewCount(0).build());

        kbArticleRepository.save(KbArticle.builder()
                .title("How do I report a lost item?")
                .content("Go to Lost & Found from your dashboard, choose 'Report an Item', select 'Lost', and fill in the description, location, and date. Other students can then search for it.")
                .category(general).author(author).status(KbArticleStatus.PUBLISHED).viewCount(0).build());

        kbArticleRepository.save(KbArticle.builder()
                .title("How do I check my ticket status?")
                .content("Go to My Tickets from your student dashboard. Every ticket shows its current status (Open, In Progress, Resolved, Closed) and any staff replies.")
                .category(general).author(author).status(KbArticleStatus.PUBLISHED).viewCount(0).build());

        log.info("Seeded baseline knowledge base articles.");
    }

    private void seedSemesterCatalog() {
        if (semesterRepository.count() > 0) {
            return;
        }
        Semester semester = semesterRepository.save(Semester.builder()
                .name("2026 Semester 2")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(4))
                .registrationOpen(true)
                .build());

        Course se2030 = courseRepository.save(Course.builder().code("SE2030").name("Software Engineering").credits(4).build());
        Course it1010 = courseRepository.save(Course.builder().code("IT1010").name("Introduction to Programming").credits(3).build());

        offeringRepository.save(CourseOffering.builder().semester(semester).course(se2030).capacity(60).scheduleInfo("Mon/Wed 09:00-11:00").build());
        offeringRepository.save(CourseOffering.builder().semester(semester).course(it1010).capacity(80).scheduleInfo("Tue/Thu 13:00-15:00").build());

        log.info("Seeded an open semester with a sample course catalog.");
    }
}
