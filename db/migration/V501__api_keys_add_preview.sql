-- Add key_preview column to api_keys table
-- Stores a masked preview of the API key (e.g., sk_12345678...abcd) for UI display
-- This avoids hashing already-hashed values (which produces meaningless masks)
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE api_keys ADD (key_preview VARCHAR2(20))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN
            NULL; -- Column already exists
        ELSE
            RAISE;
        END IF;
END;
/

-- Populate key_preview with a generic placeholder for existing keys
-- Future keys will have specific previews computed from the raw key before hashing
BEGIN
    EXECUTE IMMEDIATE 'UPDATE api_keys SET key_preview = ''sk_••••••••'' WHERE key_preview IS NULL';
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -904 THEN  -- ORA-00904: invalid identifier (column doesn't exist yet)
            RAISE;
        END IF;
END;
/

-- Create index on key_preview for future query optimization (if needed)
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_api_key_preview ON api_keys(key_preview)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            NULL; -- Index already exists
        ELSE
            RAISE;
        END IF;
END;
/
