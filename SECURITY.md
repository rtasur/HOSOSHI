# Security

HOSOSHI is an internal hackathon prototype and is not a production law-enforcement deployment.

## Reporting an issue
For team development, report security issues privately to the repository maintainers rather than opening a public issue containing secrets or sensitive details.

## Never commit
- `.env` files
- passwords
- JWT secrets
- API keys
- private certificates/keys
- real criminal or personally identifying investigative records

## Prototype safeguards
The current application includes:
- JWT-based authentication
- role-based authorization
- case-level access control
- case assignment
- audit logging

Before any real-world deployment, perform a dedicated security review and add appropriate production controls.
