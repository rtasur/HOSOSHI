# Security Notes

HOSOSHI is an SIH/research prototype, not a production law-enforcement deployment.

## Current prototype controls

- Spring Security authentication
- JWT-based stateless sessions
- Role-based authorization
- Case-level access control
- Audit logging
- SHA-256 evidence metadata and audit events for uploads/views
- File type/size allowlisting and path traversal protection
- Evidence upload is blocked until the case contains a real source document
- Security response headers
- Optional AES-256-GCM application-layer JSON API payload protection
- Docker-isolated local services

## API encryption

When enabled, the prototype uses AES-256-GCM with a 32-byte Base64 key for operational JSON API request/response payloads. Authentication/bootstrap endpoints intentionally remain normal JSON. This feature is defense-in-depth and **does not replace HTTPS/TLS**.

Because the frontend needs the key for this prototype wrapper, the browser-visible key is not a production server secret.

## Secrets

Never commit:

- `.env`
- JWT secrets intended for non-demo deployments
- production passwords
- private keys/API keys
- database dumps
- real investigative or personally identifiable information

Use `.env.example` for safe local demonstration configuration.

## OSINT / controlled dark-web roadmap

Future collection components must operate only on lawful, authorized and approved sources. They must not bypass authentication, access controls or security mechanisms.

## Prototype data

Use synthetic/demo investigative data only when sharing or publishing the repository.
