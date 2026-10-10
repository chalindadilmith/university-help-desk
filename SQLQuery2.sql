USE helpdesk_db;
ALTER LOGIN helpdesk_user WITH PASSWORD = 'YourStrongPassword123!';
ALTER ROLE db_owner ADD MEMBER helpdesk_user;