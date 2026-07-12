# Android Vertical Slice Template

Copy this section into the active phase document for each screen or tightly related behavior group.

## `<Requirement IDs>` — `<Feature name>`

### Reference

- iOS views:
- iOS view models/services:
- iOS tests:
- Backend routes/services/validators:
- Product specs:

### Product behavior

- Entry point:
- Primary action:
- Secondary actions:
- Exit/navigation:
- Persistence/lifecycle behavior:

### Contract

- Endpoint/method:
- Authentication role:
- Request:
- Response:
- Time zone/date encoding:
- Error codes and required UI outcome:
- Idempotency/concurrency behavior:

### Required states

- [ ] Loading
- [ ] Empty
- [ ] Typical content
- [ ] Long content
- [ ] Updating/disabled
- [ ] Validation error
- [ ] Network/retry
- [ ] Unauthorized/forbidden
- [ ] Conflict/domain error
- [ ] Confirmation
- [ ] Success/partial success

Remove non-applicable states only with a short reason.

### UI reference captures

| State | iOS evidence | Android evidence | Result |
|---|---|---|---|
| Typical | | | |
| Empty | | | |
| Error | | | |
| Large text | | | |
| Dark mode | | | |

### Automated verification

- Contract tests:
- Repository/state tests:
- Compose UI tests:
- Accessibility tests/checks:

### Manual verification

- Emulator/device:
- Android version:
- Back/keyboard/lifecycle result:
- Notification/deep-link result if applicable:

### Completion

- [ ] All scoped parity rows are `VERIFIED`.
- [ ] No material visual mismatch remains.
- [ ] Physical-device verification completed.
- [ ] Documentation and evidence updated.
