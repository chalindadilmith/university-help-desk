package com.helpdesk.config;

import com.helpdesk.entity.DegreeSpecialisation;
import com.helpdesk.entity.Registration;
import com.helpdesk.entity.Role;
import com.helpdesk.entity.User;
import com.helpdesk.repository.DegreeSpecialisationRepository;
import com.helpdesk.repository.RegistrationRepository;
import com.helpdesk.repository.UserRepository;
import com.helpdesk.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * Brings an EXISTING helpdesk_db up to date for the semester-registration
 * approval workflow. Runs once at every startup, after Hibernate's
 * ddl-auto=update has added the new tables/columns, and is safe to re-run.
 *
 * Hibernate's "update" mode can add columns and tables, but it cannot:
 *  1. change the CHECK constraint it generated for registrations.status
 *     (old: PENDING/CONFIRMED/CANCELLED -> new: PENDING/APPROVED/REJECTED/CANCELLED),
 *  2. convert old CONFIRMED rows to APPROVED,
 *  3. create SQL Server FILTERED unique indexes (needed because a plain UNIQUE
 *     constraint on a nullable column allows only one NULL),
 *  4. fill in the new generated IDs for rows that already exist.
 * This class does exactly those four things, plus seeds the default degree
 * specialisations if the table is empty.
 *
 * The same SQL is also provided as database/semester-registration-migration.sql
 * for anyone who prefers to run it by hand in SSMS.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // after DatabaseResetRunner, before DemoDataSeeder (which needs the specialisations)
public class RegistrationSchemaInitializer implements ApplicationRunner {

    private static final String[] SQL_SERVER_STATEMENTS = {
            // 1. drop whatever CHECK constraint(s) currently sit on registrations.status
            """
            DECLARE @sql nvarchar(max) = N'';
            SELECT @sql = @sql + N'ALTER TABLE registrations DROP CONSTRAINT ' + QUOTENAME(cc.name) + N';'
            FROM sys.check_constraints cc
            JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id
            WHERE cc.parent_object_id = OBJECT_ID('registrations') AND c.name = 'status';
            IF LEN(@sql) > 0 EXEC sp_executesql @sql;
            """,
            // 2. old CONFIRMED status no longer exists
            "UPDATE registrations SET status = 'APPROVED' WHERE status = 'CONFIRMED'",
            // 3. re-add the constraint with the new status values
            "ALTER TABLE registrations ADD CONSTRAINT ck_registrations_status " +
                    "CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))",
            // 4. unique (but nullable) generated IDs
            """
            IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_users_student_code' AND object_id = OBJECT_ID('users'))
                CREATE UNIQUE INDEX ux_users_student_code ON users(student_code) WHERE student_code IS NOT NULL;
            """,
            """
            IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_registrations_reference_no' AND object_id = OBJECT_ID('registrations'))
                CREATE UNIQUE INDEX ux_registrations_reference_no ON registrations(reference_no) WHERE reference_no IS NOT NULL;
            """
    };

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final DegreeSpecialisationRepository specialisationRepository;

    public RegistrationSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate,
                                         TransactionTemplate transactionTemplate, UserRepository userRepository,
                                         RegistrationRepository registrationRepository,
                                         DegreeSpecialisationRepository specialisationRepository) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.specialisationRepository = specialisationRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isSqlServer()) {
            for (String sql : SQL_SERVER_STATEMENTS) {
                try {
                    jdbcTemplate.execute(sql);
                } catch (Exception e) {
                    log.warn("Registration schema update step failed (continuing): {}", e.getMessage());
                }
            }
        }
        transactionTemplate.executeWithoutResult(status -> {
            seedSpecialisations();
            backfillStudentCodes();
            backfillRegistrationReferences();
        });
    }

    private boolean isSqlServer() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("sql server");
        } catch (Exception e) {
            log.warn("Could not detect database type: {}", e.getMessage());
            return false;
        }
    }

    private void seedSpecialisations() {
        if (specialisationRepository.count() > 0) {
            return;
        }
        String[][] defaults = {
                {"IT", "Information Technology"},
                {"SE", "Software Engineering"},
                {"CSNE", "Computer Systems & Network Engineering"},
                {"ISE", "Information Systems Engineering"},
                {"CS", "Cyber Security"},
                {"IM", "Interactive Media"},
                {"DS", "Data Science"}
        };
        for (String[] d : defaults) {
            specialisationRepository.save(DegreeSpecialisation.builder().code(d[0]).name(d[1]).active(true).build());
        }
        log.info("Seeded {} default degree specialisations.", defaults.length);
    }

    private void backfillStudentCodes() {
        List<User> students = userRepository.findByRoleAndStudentCodeIsNull(Role.STUDENT);
        for (User student : students) {
            student.setStudentCode(IdGenerator.studentCode(student.getId(), student.getCreatedAt()));
        }
        if (!students.isEmpty()) {
            log.info("Assigned student IDs to {} existing student account(s).", students.size());
        }
    }

    private void backfillRegistrationReferences() {
        List<Registration> registrations = registrationRepository.findByReferenceNoIsNull();
        for (Registration r : registrations) {
            r.setReferenceNo(IdGenerator.registrationReference(r.getId(), r.getCreatedAt()));
        }
        if (!registrations.isEmpty()) {
            log.info("Assigned registration numbers to {} existing registration(s).", registrations.size());
        }
    }
}
