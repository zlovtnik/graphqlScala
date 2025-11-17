# Security Architecture

## Overview

This document describes the current security architecture, baseline security controls, and risk assessment for SSF GraphQL.

## Purpose

- Inventory all authentication and authorization mechanisms
- Document baseline security controls currently in place
- Identify security boundaries and trust assumptions
- Serve as reference for compliance and security reviews

## Owner

Security & Architecture team

## Baseline Security Controls

### Authentication
- **JWT-based Authentication**: HS256 tokens with entropy validation
- **Token Validation**: JwtAuthenticationFilter extracts and validates tokens from Authorization headers
- **Stateless API**: No server-side sessions; all context encoded in JWT

### Authorization
- **HTTP-Level Authorization**: Spring Security SecurityFilterChain enforces endpoint access rules
- **GraphQL-Level Authorization**: GraphQLAuthorizationInstrumentation enforces operation-level auth before data fetchers
- **Field-Level Authorization**: Planned in Phase 4

### Encryption & Secrets
- **TLS/HTTPS**: Jetty TLS on port 8443 with certificate from keystore.p12
- **Environment Secrets**: JWT_SECRET, MINIO credentials validated at startup via EnvironmentValidator
- **Password Security**: Database passwords stored in chmod 600 files, cleared from memory after use

## Current Risk Assessment

### Low Risk
- JWT token structure and HS256 algorithm implementation
- Stateless architecture reducing session-related vulnerabilities

### Medium Risk
- Missing field-level authorization (Phase 4)
- Audit log implementation incomplete (Phase 2)
- No data encryption at rest (Phase 3)

### High Risk
- TBD during security review

## Sections to Be Filled In Later

- [ ] Detailed threat model
- [ ] Security test coverage matrix
- [ ] Incident response procedures
- [ ] Security review checklist

## References

- See README.md for Phase roadmap
- See COMPLIANCE_ACCEPTANCE_CRITERIA.md for GDPR/SOX requirements
