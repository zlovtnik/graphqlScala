-- Create users table (skip if exists)
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE users (
        id VARCHAR2(36) PRIMARY KEY,
        username VARCHAR2(255) NOT NULL UNIQUE,
        password VARCHAR2(255) NOT NULL,
        email VARCHAR2(255) NOT NULL UNIQUE,
        created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
        updated_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
    )';
EXCEPTION
    WHEN OTHERS THEN
        NULL; -- Table already exists
END;
/