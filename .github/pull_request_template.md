## Summary
<!-- What problem does this PR solve? -->

## Changes
- <!-- Key change 1 -->
- <!-- Key change 2 -->

## Testing
- [ ] `mvn -B clean verify` passed

## Hexagonal Constraints Checklist
- [ ] No integration tests outside `hex-application`
- [ ] No DB drivers in adapter `pom.xml` files
- [ ] Adapter dependencies are inward-only (toward core/application composition)

## Notes
<!-- Risks, follow-ups, migration notes, or rollout considerations -->

