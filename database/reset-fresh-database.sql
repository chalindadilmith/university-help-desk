-- Wipes ALL data from the Help Desk database (SQL Server) and keeps the schema.
-- After running this, start the app: DemoDataSeeder re-creates only the admin and staff accounts
-- (admin@helpdesk.university / Admin@123, staff@helpdesk.university / Staff@123) plus the baseline
-- categories, routing rules, FAQ articles and one open semester.
-- Also delete the uploads/ticket-attachments and uploads/lost-found-proofs folders.
-- Easier alternative: start the app once with RESET_DATABASE=true (does all of this for you).
-- Run this against the helpdesk database (USE <your database name>; first).

EXEC sp_MSforeachtable 'ALTER TABLE ? NOCHECK CONSTRAINT ALL';
EXEC sp_MSforeachtable 'DELETE FROM ?';
EXEC sp_MSforeachtable 'ALTER TABLE ? WITH CHECK CHECK CONSTRAINT ALL';
EXEC sp_MSforeachtable 'IF OBJECTPROPERTY(OBJECT_ID(''?''), ''TableHasIdentity'') = 1 DBCC CHECKIDENT (''?'', RESEED, 0)';
