#!/usr/bin/env python3
import subprocess
import secrets
import hashlib
import base64
import os
import tempfile
from pathlib import Path

# Generate a strong random password
strong_password = secrets.token_urlsafe(32)

# Write password to a temporary file with restrictive permissions (0600)
# Using NamedTemporaryFile ensures the file is securely created
temp_file = tempfile.NamedTemporaryFile(mode='w', delete=False, suffix='.txt', prefix='bcrypt_password_')
try:
    # Set restrictive permissions (owner-only read/write)
    os.chmod(temp_file.name, 0o600)
    
    # Write the password to the file
    temp_file.write(strong_password)
    temp_file.close()
    
    # Print only the file path, NOT the secret
    print(f"Password written to: {temp_file.name}")
    print("File permissions: 0600 (owner-only)")
    print(f"\nTo generate the bcrypt hash:")
    print("1. Install bcrypt: pip install bcrypt")
    print(f"2. Run: python3 -c \"import bcrypt; passwd=$(cat {temp_file.name}); print(bcrypt.hashpw(passwd.encode(), bcrypt.gensalt(rounds=12)).decode())\"")
    print(f"\n⚠️  IMPORTANT: Delete the file after use: rm {temp_file.name}")
    print("⚠️  Never commit this password file to version control")
    print("⚠️  For production, store passwords in a secrets manager (e.g., AWS Secrets Manager, Vault)")
    
except Exception as e:
    print(f"Error: {e}", file=__import__('sys').stderr)
    raise
