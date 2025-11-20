-- Create indexes for roles table
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_roles_name ON roles(name)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE IN (-955, -1408) THEN
            NULL;
        ELSE
            RAISE;
        END IF;
END;
/
