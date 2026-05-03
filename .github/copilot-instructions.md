# Copilot Repository Instructions

This repository is a Spring Boot Avro REST service.

## Code and architecture conventions
- Keep Java code under `com.example.user`.
- Keep controllers in `api`, config in `config`, and generated Avro models in `model`.
- Do not manually edit generated sources in `target/generated-sources/avro`.
- When changing `src/main/avro/user_event.avsc`, regenerate sources with Maven.
- Prefer small, focused PRs and minimal diffs.

## Build and quality gates
- Run before proposing changes:
  - `mvn -B clean verify`
- If dependencies changed, also run:
  - `mvn -B org.owasp:dependency-check-maven:check`
- Treat HIGH and CRITICAL findings as release blockers.

## Security expectations
- Do not hardcode credentials, tokens, or secrets.
- Keep actuator exposure minimal and explicit.
- Validate request payloads and avoid permissive CORS defaults.
- Prefer dependency upgrades that are patch/minor unless otherwise required.

## Output expectations for PR assistance
When proposing security/dependency changes, include:
- impacted dependency and version
- CVE and severity
- minimum fixed version
- brief risk/compatibility notes

