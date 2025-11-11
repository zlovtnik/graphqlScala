# SQL Schema Organization

This directory contains the Oracle database schema organized by object types.

## Structure

- `master.sql`: Main script that includes all schema files in the correct order
- `sequences/`: Database sequences
- `types/`: Custom Oracle types
- `tables/`: Table creation scripts
- `packages/`: Package specifications and bodies
- `users/`: User creation and management scripts
- `grants/`: Privilege granting scripts

## Usage

1. Run `master.sql` as the application user to create all schema objects
2. Run scripts in `users/` and `grants/` as SYS/DBA user to set up the application user

## Notes

- All scripts are idempotent where possible
- Audit tables and sequences are included for logging functionality
- The dynamic CRUD package provides generic operations for allowed tables
