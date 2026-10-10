package com.helpdesk.config;

import com.helpdesk.entity.*;
import com.helpdesk.repository.*;
import com.helpdesk.util.IdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DEV/DEMO ONLY - fills the app with realistic Sri Lankan sample data (students, staff,
 * tickets with replies and ratings, lost and found items, knowledge base articles,
 * more courses and notifications) so the screens do not look empty.
 *
 * Safe to run many times: it does nothing if the first sample student already exists.
 * Turn it off with SEED_SAMPLE_DATA=false. Every sample account uses the password
 * Student@123 (students) or Staff@123 (staff).
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SampleDataSeeder implements CommandLineRunner {

    private static final String MARKER_EMAIL = "nimali.perera@students.university.lk";

    private final DemoDataSeeder baseSeeder;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
    private final FeedbackRepository feedbackRepository;
    private final TicketCategoryRepository categoryRepository;
    private final JobPoolRepository poolRepository;
    private final LostFoundItemRepository lostFoundItemRepository;
    private final KbCategoryRepository kbCategoryRepository;
    private final KbArticleRepository kbArticleRepository;
    private final NotificationRepository notificationRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository offeringRepository;
    private final SemesterRepository semesterRepository;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager em;

    @Value("${app.seed-sample-data:true}")
    private boolean enabled;

    public SampleDataSeeder(DemoDataSeeder baseSeeder, UserRepository userRepository, PasswordEncoder passwordEncoder,
                            TicketRepository ticketRepository, TicketCommentRepository commentRepository,
                            FeedbackRepository feedbackRepository, TicketCategoryRepository categoryRepository,
                            JobPoolRepository poolRepository, LostFoundItemRepository lostFoundItemRepository,
                            KbCategoryRepository kbCategoryRepository, KbArticleRepository kbArticleRepository,
                            NotificationRepository notificationRepository, CourseRepository courseRepository,
                            CourseOfferingRepository offeringRepository, SemesterRepository semesterRepository,
                            JdbcTemplate jdbc) {
        this.baseSeeder = baseSeeder;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.feedbackRepository = feedbackRepository;
        this.categoryRepository = categoryRepository;
        this.poolRepository = poolRepository;
        this.lostFoundItemRepository = lostFoundItemRepository;
        this.kbCategoryRepository = kbCategoryRepository;
        this.kbArticleRepository = kbArticleRepository;
        this.notificationRepository = notificationRepository;
        this.courseRepository = courseRepository;
        this.offeringRepository = offeringRepository;
        this.semesterRepository = semesterRepository;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("app.seed-sample-data=false - skipping sample data.");
            return;
        }
        baseSeeder.run(); // make sure admin, staff, categories, pool exist first (it is idempotent)
        if (userRepository.existsByEmail(MARKER_EMAIL)) {
            return;
        }

        List<User> students = seedStudents();
        List<User> staff = seedStaff();
        seedTickets(students, staff);
        seedLostAndFound(students);
        seedKnowledgeBase();
        seedCourses();
        em.flush();
        log.info("Seeded sample Sri Lankan data: {} students, {} extra staff. Student password: Student@123, staff password: Staff@123",
                students.size(), staff.size());
    }

    // ------------------------------------------------------------------ users
    private List<User> seedStudents() {
        String[][] data = {
                {"Nimali Perera", "IT24100101", "nimali.perera"},
                {"Kasun Fernando", "IT24100102", "kasun.fernando"},
                {"Tharushi Jayawardena", "IT24100103", "tharushi.jayawardena"},
                {"Dilshan Silva", "IT24100104", "dilshan.silva"},
                {"Sachini Wickramasinghe", "IT24100105", "sachini.wickramasinghe"},
                {"Ravindu Gunasekara", "IT24100106", "ravindu.gunasekara"},
                {"Hasini Rajapaksha", "IT24100107", "hasini.rajapaksha"},
                {"Chamath Bandara", "IT24100108", "chamath.bandara"},
                {"Ishara Senanayake", "IT25100201", "ishara.senanayake"},
                {"Lakshan Dissanayake", "IT25100202", "lakshan.dissanayake"},
                {"Amaya Karunaratne", "IT25100203", "amaya.karunaratne"},
                {"Yasiru Herath", "IT25100204", "yasiru.herath"},
                {"Pavithra Ranasinghe", "IT25100205", "pavithra.ranasinghe"},
                {"Sanduni Abeysekara", "IT25100206", "sanduni.abeysekara"},
                {"Thilina Amarasinghe", "IT25100207", "thilina.amarasinghe"},
                {"Nethmi Liyanage", "IT25100208", "nethmi.liyanage"},
                {"Vishmi Gamage", "IT23100301", "vishmi.gamage"},
                {"Mohamed Rizwan", "IT23100302", "mohamed.rizwan"},
                {"Sivaruban Thavarajah", "IT23100303", "sivaruban.thavarajah"},
                {"Anjalee Weerasinghe", "IT23100304", "anjalee.weerasinghe"},
        };
        String hash = passwordEncoder.encode("Student@123");
        List<User> out = new ArrayList<>();
        int i = 0;
        for (String[] d : data) {
            User u = userRepository.save(User.builder()
                    .fullName(d[0]).universityId(d[1])
                    .email(d[2] + "@students.university.lk")
                    .passwordHash(hash).role(Role.STUDENT).status(AccountStatus.ACTIVE)
                    .mustChangePassword(false).build());
            u.setStudentCode(IdGenerator.studentCode(u.getId(), u.getCreatedAt()));
            userRepository.save(u);
            out.add(u);
            i++;
        }
        em.flush();
        for (int k = 0; k < out.size(); k++) {
            // spread sign-up and last-login dates over the last 5 months
            jdbc.update("UPDATE users SET created_at = ?, last_login_at = ? WHERE id = ?",
                    LocalDateTime.now().minusDays(150 - k * 6L), LocalDateTime.now().minusHours(3 + k * 7L), out.get(k).getId());
        }
        return out;
    }

    private List<User> seedStaff() {
        String[][] data = {
                {"Mahesh Jayasuriya", "STF-0101", "mahesh.jayasuriya"},
                {"Priyanka Samarasinghe", "STF-0102", "priyanka.samarasinghe"},
                {"Ruwan Pathirana", "STF-0103", "ruwan.pathirana"},
                {"Dinithi Cooray", "STF-0104", "dinithi.cooray"},
        };
        String hash = passwordEncoder.encode("Staff@123");
        List<User> out = new ArrayList<>();
        for (String[] d : data) {
            out.add(userRepository.save(User.builder()
                    .fullName(d[0]).universityId(d[1]).email(d[2] + "@helpdesk.university")
                    .passwordHash(hash).role(Role.STAFF).status(AccountStatus.ACTIVE)
                    .mustChangePassword(false).build()));
        }
        JobPool pool = poolRepository.findAllByOrderByNameAsc().stream().findFirst().orElse(null);
        if (pool != null) {
            pool.getStaffMembers().addAll(out);
            poolRepository.save(pool);
        }
        return out;
    }

    // ---------------------------------------------------------------- tickets
    private record T(int student, String category, Priority priority, TicketStatus status, String subject,
                     String description, int daysAgo, int staff, String reply, Integer rating, String feedback) {
    }

    private void seedTickets(List<User> students, List<User> staff) {
        List<T> list = List.of(
                new T(0, "IT", Priority.HIGH, TicketStatus.RESOLVED, "Cannot log in to the LMS after password change",
                        "I changed my university password yesterday and now the LMS says my credentials are invalid. I have tried on my phone and laptop.",
                        21, 0, "We cleared a stale session on the LMS side and re-synced your account. Please log out of all devices and sign in again with your new password.", 5, "Fixed within an hour, thank you so much!"),
                new T(1, "IT", Priority.MEDIUM, TicketStatus.RESOLVED, "Wi-Fi keeps disconnecting in the Computing Faculty",
                        "The eduroam connection drops every few minutes on the 3rd floor labs. It is very difficult to submit lab work.",
                        18, 1, "The access point on the 3rd floor was overloaded and has been replaced. Please forget the network on your device and reconnect.", 4, "Better now, thanks."),
                new T(2, "Academic", Priority.MEDIUM, TicketStatus.CLOSED, "Request for official transcript for a scholarship",
                        "I need an official transcript for the Mahapola scholarship application. The closing date is next Friday.",
                        30, 2, "Your transcript has been issued and is ready for collection at the Examinations Branch with your student ID.", 5, "Very quick and helpful."),
                new T(3, "Facilities", Priority.LOW, TicketStatus.RESOLVED, "Projector not working in Lecture Hall B2",
                        "The projector in B2 shows a blue screen. Our Software Engineering lecture was delayed by 20 minutes.",
                        14, 3, "A technician replaced the HDMI cable and tested the projector. It is working normally.", 3, "Took two days, but it works now."),
                new T(4, "IT", Priority.HIGH, TicketStatus.IN_PROGRESS, "University email storage full",
                        "My student mailbox says it is 99% full and I am not receiving lecturer emails. I have not received my group project updates.",
                        3, 0, "We are increasing your mailbox quota. This needs approval from the IT director and should be done by tomorrow.", null, null),
                new T(5, "Academic", Priority.MEDIUM, TicketStatus.OPEN, "Wrong grade shown for IT2040 in the results portal",
                        "My IT2040 Database Management Systems result shows a C, but my paper script mark total was 68 which should be a B.",
                        1, -1, null, null, null),
                new T(6, "Academic", Priority.LOW, TicketStatus.IN_PROGRESS, "Want to change my specialisation to Software Engineering",
                        "I am a second year student currently in Information Systems and would like to move to Software Engineering. What is the process?",
                        6, 1, "Please fill the specialisation change form and attach your first year GPA. The Head of Department will review it in the next meeting.", null, null),
                new T(7, "Facilities", Priority.MEDIUM, TicketStatus.ESCALATED, "Broken air conditioning in Computer Lab 4",
                        "The air conditioning in Lab 4 has not worked for a week. The room is extremely hot and the computers keep shutting down.",
                        9, 2, "This has been escalated to the Estates Department because it needs an external contractor.", null, null),
                new T(8, "IT", Priority.LOW, TicketStatus.OPEN, "How to install Visual Studio under the student licence",
                        "I could not find the student licence key for Visual Studio. Could someone tell me where to download it?",
                        0, -1, null, null, null),
                new T(9, "IT", Priority.MEDIUM, TicketStatus.RESOLVED, "VPN not connecting from home",
                        "I am in Kandy and the university VPN says authentication failed. I need it to reach the library journals.",
                        12, 1, "Your VPN profile had expired. We have renewed it, please download the new configuration file from the portal.", 5, "Works perfectly, thanks!"),
                new T(10, "Academic", Priority.HIGH, TicketStatus.RESOLVED, "Missing mid-semester marks for SE2030",
                        "My SE2030 mid-semester marks are not on the portal even though other students already see theirs.",
                        16, 3, "The lecturer had not submitted the last batch. Marks have been uploaded and are visible now.", 4, "Resolved, appreciate the follow-up."),
                new T(11, "Facilities", Priority.LOW, TicketStatus.CLOSED, "Water dispenser empty near the library",
                        "The water dispenser on the ground floor of the library has been empty for days.",
                        40, 3, "The dispenser was refilled and added to the daily maintenance checklist.", 4, "Thanks."),
                new T(12, "IT", Priority.HIGH, TicketStatus.REOPENED, "Student portal shows my fees as unpaid",
                        "I paid the semester fee at the People's Bank branch on Monday but the portal still shows it as unpaid and I cannot register for modules.",
                        8, 0, "We forwarded the receipt to Finance. They are checking the bank statement. We will confirm once matched.", null, null),
                new T(13, "Academic", Priority.MEDIUM, TicketStatus.OPEN, "Clash between IT2040 lab and SE2030 lecture",
                        "My IT2040 lab on Friday overlaps with my SE2030 tutorial. Can I be moved to another lab group?",
                        2, -1, null, null, null),
                new T(14, "IT", Priority.LOW, TicketStatus.RESOLVED, "Request for a software list for the labs",
                        "Which software is installed on the labs so I can match my laptop for assignments?",
                        25, 2, "Please check the Knowledge Base article 'Software available in computer labs'. We have also attached the list to this ticket.", 5, "Useful."),
                new T(15, "Facilities", Priority.HIGH, TicketStatus.IN_PROGRESS, "Water leak in the 2nd floor washroom",
                        "There is a water leak in the 2nd floor washroom of the Engineering building. The floor is slippery and unsafe.",
                        2, 3, "Maintenance has been informed and the area has been marked with warning signs while it is repaired.", null, null),
                new T(16, "Academic", Priority.MEDIUM, TicketStatus.RESOLVED, "Need a letter confirming student status for a visa",
                        "I need a letter confirming that I am a full-time student to apply for an internship visa.",
                        19, 1, "Your letter has been signed by the Registrar and is ready at the Student Services counter.", 5, "Fast service!"),
                new T(17, "IT", Priority.MEDIUM, TicketStatus.CLOSED, "Cannot print from lab computers",
                        "The lab printers say 'Offline' when I try to print my report.",
                        35, 0, "The print server was restarted and the lab printers are online again.", 3, "OK."),
                new T(18, "Academic", Priority.LOW, TicketStatus.OPEN, "How to apply for a repeat exam",
                        "I failed one module last semester. How do I apply for the repeat examination and what is the fee?",
                        1, -1, null, null, null),
                new T(19, "IT", Priority.HIGH, TicketStatus.RESOLVED, "Account locked after multiple login attempts",
                        "My account is locked after I typed the wrong password several times and I have a quiz in one hour.",
                        5, 2, "Your account was unlocked and a password reset link was sent to your university email.", 5, "You saved my quiz, thank you!"),
                new T(2, "Facilities", Priority.LOW, TicketStatus.OPEN, "Lights flickering in the library reading room",
                        "The lights in the 2nd floor reading room flicker and cause headaches during exam week.",
                        0, -1, null, null, null),
                new T(4, "Academic", Priority.MEDIUM, TicketStatus.RESOLVED, "Request to defer a module to next semester",
                        "I had a medical issue and could not attend the midterm. Can I defer IT2040 to next semester?",
                        27, 1, "Your medical certificate has been accepted and the module is deferred. You can register for it again next semester.", 4, "Understanding and helpful."),
                new T(6, "IT", Priority.MEDIUM, TicketStatus.RESOLVED, "GitHub Education pack for students",
                        "How can I claim the free GitHub Student Developer Pack with my university email?",
                        33, 0, "Use your @students.university.lk email on the GitHub Education page. We confirmed your enrolment so you should be approved shortly.", 5, "Got it, thanks!")
        );

        JobPool pool = poolRepository.findAllByOrderByNameAsc().stream().findFirst().orElse(null);
        int n = 0;
        for (T t : list) {
            TicketCategory cat = categoryRepository.findAllByOrderByNameAsc().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(t.category())).findFirst().orElse(null);
            if (cat == null) {
                continue;
            }
            User student = students.get(t.student());
            User handler = t.staff() >= 0 ? staff.get(t.staff() % staff.size()) : null;
            LocalDateTime created = LocalDateTime.now().minusDays(t.daysAgo()).minusHours(2 + n % 9);

            Ticket saved = ticketRepository.save(Ticket.builder()
                    .ticketRef("TMP" + System.nanoTime() % 100000000L + n)
                    .student(student).category(cat).assignedStaff(handler)
                    .routedPool(cat.getPool() != null ? cat.getPool() : pool)
                    .subject(t.subject()).description(t.description())
                    .status(t.status()).priority(t.priority()).build());
            saved.setTicketRef(String.format("TCK-%06d", saved.getId()));
            ticketRepository.save(saved);
            em.flush();

            LocalDateTime lastTouch = created.plusHours(5);
            boolean done = t.status() == TicketStatus.RESOLVED || t.status() == TicketStatus.CLOSED;
            LocalDateTime resolved = done ? created.plusHours(20 + n % 30) : null;

            Long replyId = null;
            if (t.reply() != null && handler != null) {
                TicketComment c = commentRepository.save(TicketComment.builder()
                        .ticket(saved).author(handler).comment(t.reply())
                        .resolutionNote(done).internal(false).build());
                replyId = c.getId();
                em.flush();
                jdbc.update("UPDATE ticket_comments SET created_at = ? WHERE id = ?", created.plusHours(6), replyId);
                if (done) {
                    lastTouch = resolved;
                } else {
                    lastTouch = created.plusHours(6);
                }
            }
            if (t.status() == TicketStatus.ESCALATED && handler != null) {
                TicketComment internal = commentRepository.save(TicketComment.builder()
                        .ticket(saved).author(handler)
                        .comment("Internal note: contractor quote requested, follow up on Monday.")
                        .internal(true).build());
                em.flush();
                jdbc.update("UPDATE ticket_comments SET created_at = ? WHERE id = ?", created.plusHours(7), internal.getId());
            }
            if (t.status() == TicketStatus.REOPENED) {
                TicketComment again = commentRepository.save(TicketComment.builder()
                        .ticket(saved).author(student)
                        .comment("It is still showing as unpaid today. I have attached the bank slip again.").build());
                em.flush();
                jdbc.update("UPDATE ticket_comments SET created_at = ? WHERE id = ?", created.plusHours(30), again.getId());
                lastTouch = created.plusHours(30);
            }

            jdbc.update("UPDATE tickets SET created_at = ?, updated_at = ? WHERE id = ?", created, lastTouch, saved.getId());
            if (resolved != null) {
                jdbc.update("UPDATE tickets SET resolved_at = ? WHERE id = ?", resolved, saved.getId());
            }

            if (t.rating() != null && handler != null) {
                Feedback f = feedbackRepository.save(Feedback.builder()
                        .ticket(saved).student(student).staff(handler)
                        .rating(t.rating()).comment(t.feedback()).build());
                em.flush();
                jdbc.update("UPDATE feedback SET created_at = ? WHERE id = ?", resolved.plusHours(3), f.getId());
            }

            if (handler != null) {
                notificationRepository.save(Notification.builder()
                        .recipient(student).type("TICKET_STATUS")
                        .message("Ticket " + saved.getTicketRef() + " is now " + t.status().name().replace('_', ' ').toLowerCase() + ".")
                        .relatedEntityType("Ticket").relatedEntityId(saved.getId())
                        .read(done || n % 3 == 0).build());
            }
            n++;
        }
        em.flush();
    }

    // --------------------------------------------------------- lost and found
    private void seedLostAndFound(List<User> students) {
        Object[][] items = {
                {0, ItemType.LOST, "Black Casio scientific calculator with my initials N.P. on the back", "Computing Faculty - Lab 3", 4, ItemStatus.ACTIVE},
                {1, ItemType.FOUND, "Blue Nike water bottle left on a bench", "Main Library - Ground floor", 3, ItemStatus.ACTIVE},
                {2, ItemType.LOST, "Student ID card (IT24100103) in a transparent card holder", "Canteen", 2, ItemStatus.ACTIVE},
                {3, ItemType.FOUND, "Set of keys with a red Sri Lanka cricket keychain", "Lecture Hall B2", 6, ItemStatus.CLAIMED},
                {4, ItemType.LOST, "Grey Dell laptop charger (65W) in a black pouch", "Library - 2nd floor reading room", 5, ItemStatus.ACTIVE},
                {5, ItemType.FOUND, "Brown leather wallet with some cash and a bus season pass", "Auditorium entrance", 8, ItemStatus.CLAIMED},
                {6, ItemType.LOST, "White Sony wireless earbuds in a small case", "Sports Complex", 1, ItemStatus.ACTIVE},
                {7, ItemType.FOUND, "Umbrella with a floral pattern", "Bus stop near the main gate", 7, ItemStatus.ACTIVE},
                {8, ItemType.LOST, "Green notebook with Database Systems notes", "Computing Faculty - Room 204", 2, ItemStatus.ACTIVE},
                {9, ItemType.FOUND, "Silver Casio wrist watch", "Faculty Common Room", 9, ItemStatus.ARCHIVED},
                {10, ItemType.LOST, "Black USB flash drive 64GB with project files", "Lab 1", 3, ItemStatus.ACTIVE},
                {11, ItemType.FOUND, "Prescription glasses in a blue case", "Canteen", 4, ItemStatus.ACTIVE},
        };
        int n = 0;
        for (Object[] it : items) {
            int daysAgo = (Integer) it[4];
            LostFoundItem saved = lostFoundItemRepository.save(LostFoundItem.builder()
                    .reporter(students.get((Integer) it[0])).itemType((ItemType) it[1])
                    .description((String) it[2]).location((String) it[3])
                    .itemDate(LocalDate.now().minusDays(daysAgo)).status((ItemStatus) it[5]).build());
            em.flush();
            jdbc.update("UPDATE lost_found_items SET created_at = ? WHERE id = ?",
                    LocalDateTime.now().minusDays(daysAgo).minusHours(n % 5), saved.getId());
            n++;
        }
    }

    // --------------------------------------------------------- knowledge base
    private void seedKnowledgeBase() {
        User author = userRepository.findByEmail("staff@helpdesk.university").orElseGet(() -> userRepository.findAll().get(0));
        KbCategory accounts = kbCategoryRepository.save(KbCategory.builder().name("Accounts & Login").build());
        KbCategory wifi = kbCategoryRepository.save(KbCategory.builder().name("Wi-Fi & Network").build());
        KbCategory academic = kbCategoryRepository.save(KbCategory.builder().name("Academic & Exams").build());
        KbCategory campus = kbCategoryRepository.save(KbCategory.builder().name("Campus Facilities").build());

        Object[][] arts = {
                {accounts, "How do I connect to the campus Wi-Fi (eduroam)?", "Choose the eduroam network, use your full student email (for example nimali.perera@students.university.lk) as the username and your portal password. If it fails, forget the network and add it again.", 212, 48, 3},
                {wifi, "VPN setup for off-campus access to library journals", "Download the VPN client from the student portal, import your configuration file and sign in with your university credentials. The profile renews every 12 months.", 160, 35, 2},
                {accounts, "My student account is locked - what should I do?", "Accounts are locked for 15 minutes after repeated wrong passwords. Wait and try again, use Forgot password on the login page, or raise a ticket under IT with your student ID.", 305, 72, 5},
                {academic, "How to request an official transcript", "Apply through the Examinations Branch with your student ID and the purpose of the request. Normal processing takes 3 working days; urgent requests take 24 hours.", 188, 40, 4},
                {academic, "How to apply for a repeat examination", "Submit the repeat exam form to the Examinations Branch within 14 days of the results release and pay the repeat fee at the Finance counter.", 143, 29, 6},
                {academic, "What is the maximum credits I can register per semester?", "A student may register for at most 18 credits per semester. Registrations above that are rejected by the system.", 121, 31, 1},
                {campus, "Library opening hours and borrowing rules", "The library is open Monday to Friday 8.00 am to 8.00 pm and Saturday 8.30 am to 4.30 pm. Undergraduates may borrow up to 5 books for 14 days.", 254, 66, 2},
                {campus, "What should I do if I find or lose an item on campus?", "Use the Lost & Found section of this help desk. Report the item with a photo, and if you recognise an item as yours, submit a claim with proof.", 97, 22, 1},
                {wifi, "Software available in computer labs", "Labs include Visual Studio, IntelliJ IDEA, Eclipse, MySQL, SQL Server Management Studio, Android Studio and Microsoft Office. Request new software at least 4 weeks before the semester.", 175, 44, 3},
                {accounts, "How to claim the student software licences (GitHub, Microsoft)", "Sign in to the student portal and open Student Benefits. Use your university email to claim Microsoft 365 and the GitHub Student Developer Pack.", 132, 37, 2},
        };
        int n = 0;
        for (Object[] a : arts) {
            KbArticle saved = kbArticleRepository.save(KbArticle.builder()
                    .title((String) a[1]).content((String) a[2]).category((KbCategory) a[0]).author(author)
                    .status(KbArticleStatus.PUBLISHED).viewCount((Integer) a[3])
                    .helpfulCount((Integer) a[4]).notHelpfulCount((Integer) a[5]).build());
            em.flush();
            jdbc.update("UPDATE kb_articles SET created_at = ? WHERE id = ?", LocalDateTime.now().minusDays(120 - n * 9L), saved.getId());
            n++;
        }
    }

    // ---------------------------------------------------------------- courses
    private void seedCourses() {
        Semester semester = semesterRepository.findAll().stream().findFirst().orElse(null);
        Object[][] courses = {
                {"IT1020", "Introduction to Computer Systems", 3, 1, 70, "Mon 09:00-12:00"},
                {"IT1030", "Mathematics for Computing", 3, 1, 80, "Wed 13:00-16:00"},
                {"IT1050", "Object Oriented Concepts", 3, 1, 70, "Thu 09:00-12:00"},
                {"IT2020", "Data Structures and Algorithms", 4, 2, 60, "Tue 09:00-12:00"},
                {"IT2030", "Web Application Development", 4, 2, 60, "Thu 13:00-16:00"},
                {"IT3010", "Network Design and Management", 4, 3, 45, "Fri 13:00-16:00"},
                {"IT3040", "Information Security", 4, 3, 45, "Tue 13:00-16:00"},
        };
        for (Object[] c : courses) {
            String code = (String) c[0];
            if (courseRepository.existsByCodeIgnoreCase(code)) {
                continue;
            }
            Course course = courseRepository.save(Course.builder().code(code).name((String) c[1])
                    .credits((Integer) c[2]).yearLevel((Integer) c[3]).build());
            if (semester != null) {
                offeringRepository.save(CourseOffering.builder().semester(semester).course(course)
                        .capacity((Integer) c[4]).scheduleInfo((String) c[5]).build());
            }
        }
    }
}
