/* =====================================================================
   Semester Registration update - SQL Server migration for helpdesk_db
   ---------------------------------------------------------------------
   OPTIONAL: the application applies all of this automatically at startup
   (Hibernate ddl-auto=update + config/RegistrationSchemaInitializer).
   Run this script by hand in SSMS only if you want to update the database
   before starting the app, or to see exactly what changed.
   Safe to run more than once.
   ===================================================================== */
USE helpdesk_db;
GO

/* ---------- 1. Degree specialisations (new table) ---------- */
IF OBJECT_ID('degree_specialisations', 'U') IS NULL
BEGIN
    CREATE TABLE degree_specialisations (
        id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        code        VARCHAR(10)  NOT NULL UNIQUE,
        name        VARCHAR(100) NOT NULL UNIQUE,
        description VARCHAR(255) NULL,
        active      BIT          NOT NULL DEFAULT 1
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM degree_specialisations)
BEGIN
    INSERT INTO degree_specialisations (code, name, active) VALUES
        ('IT',   'Information Technology', 1),
        ('SE',   'Software Engineering', 1),
        ('CSNE', 'Computer Systems & Network Engineering', 1),
        ('ISE',  'Information Systems Engineering', 1),
        ('CS',   'Cyber Security', 1),
        ('IM',   'Interactive Media', 1),
        ('DS',   'Data Science', 1);
END
GO

/* ---------- 2. users: system-generated student ID ---------- */
IF COL_LENGTH('users', 'student_code') IS NULL
    ALTER TABLE users ADD student_code VARCHAR(20) NULL;
GO

UPDATE users
SET student_code = CONCAT('STU', YEAR(created_at), RIGHT('00000' + CAST(id AS VARCHAR(20)), 5))
WHERE role = 'STUDENT' AND student_code IS NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_users_student_code' AND object_id = OBJECT_ID('users'))
    CREATE UNIQUE INDEX ux_users_student_code ON users(student_code) WHERE student_code IS NOT NULL;
GO

/* ---------- 3. courses: year level + specialisation ---------- */
IF COL_LENGTH('courses', 'year_level') IS NULL
    ALTER TABLE courses ADD year_level INT NULL CONSTRAINT ck_courses_year_level CHECK (year_level BETWEEN 1 AND 4);
GO
IF COL_LENGTH('courses', 'specialisation_id') IS NULL
    ALTER TABLE courses ADD specialisation_id BIGINT NULL
        CONSTRAINT fk_courses_specialisation REFERENCES degree_specialisations(id);
GO

/* ---------- 4. registrations: form fields + approval workflow ---------- */
IF COL_LENGTH('registrations', 'reference_no') IS NULL      ALTER TABLE registrations ADD reference_no VARCHAR(30) NULL;
IF COL_LENGTH('registrations', 'specialisation_id') IS NULL ALTER TABLE registrations ADD specialisation_id BIGINT NULL
                                                                 CONSTRAINT fk_registrations_specialisation REFERENCES degree_specialisations(id);
IF COL_LENGTH('registrations', 'academic_year') IS NULL     ALTER TABLE registrations ADD academic_year INT NULL;
IF COL_LENGTH('registrations', 'semester_of_study') IS NULL ALTER TABLE registrations ADD semester_of_study INT NULL;
IF COL_LENGTH('registrations', 'contact_number') IS NULL    ALTER TABLE registrations ADD contact_number VARCHAR(20) NULL;
IF COL_LENGTH('registrations', 'total_credits') IS NULL     ALTER TABLE registrations ADD total_credits INT NULL;
IF COL_LENGTH('registrations', 'admin_remarks') IS NULL     ALTER TABLE registrations ADD admin_remarks VARCHAR(500) NULL;
IF COL_LENGTH('registrations', 'reviewed_by') IS NULL       ALTER TABLE registrations ADD reviewed_by BIGINT NULL
                                                                 CONSTRAINT fk_registrations_reviewed_by REFERENCES users(id);
IF COL_LENGTH('registrations', 'reviewed_at') IS NULL       ALTER TABLE registrations ADD reviewed_at DATETIME2(6) NULL;
IF COL_LENGTH('registrations', 'updated_at') IS NULL        ALTER TABLE registrations ADD updated_at DATETIME2(6) NULL;
GO

/* status values changed: CONFIRMED -> APPROVED, new REJECTED.
   Hibernate generated a CHECK constraint with the old values, so drop it first. */
DECLARE @sql nvarchar(max) = N'';
SELECT @sql = @sql + N'ALTER TABLE registrations DROP CONSTRAINT ' + QUOTENAME(cc.name) + N';'
FROM sys.check_constraints cc
JOIN sys.columns c ON c.object_id = cc.parent_object_id AND c.column_id = cc.parent_column_id
WHERE cc.parent_object_id = OBJECT_ID('registrations') AND c.name = 'status';
IF LEN(@sql) > 0 EXEC sp_executesql @sql;
GO

UPDATE registrations SET status = 'APPROVED' WHERE status = 'CONFIRMED';
GO

ALTER TABLE registrations ADD CONSTRAINT ck_registrations_status
    CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'));
GO

UPDATE registrations
SET reference_no = CONCAT('SR', YEAR(created_at), '-', RIGHT('000000' + CAST(id AS VARCHAR(20)), 6))
WHERE reference_no IS NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ux_registrations_reference_no' AND object_id = OBJECT_ID('registrations'))
    CREATE UNIQUE INDEX ux_registrations_reference_no ON registrations(reference_no) WHERE reference_no IS NOT NULL;
GO

PRINT 'Semester registration migration complete.';
