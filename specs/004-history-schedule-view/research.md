# Research: History Schedule View (004)

## Decision 1: Shared schedule + status derivation

- **Decision**: Add a shared `getScheduleWithStatus(patientId, from, to, tz)` in the schedule service to return scheduled doses plus derived status.
- **Rationale**: Prevents duplication across month/day endpoints and aligns with existing “today” logic.
- **Alternatives considered**: Separate derivation per endpoint (more duplication, harder to test).

## Decision 2: Month window and timezone

- **Decision**: Support navigation across last 3 months (including current) and show future days to month-end using Asia/Tokyo boundaries.
- **Rationale**: Balances usefulness with data volume while matching MVP timezone rules.
- **Alternatives considered**: Current month only (less useful), full history (more load/complexity).

## Decision 3: Slot summary aggregation

- **Decision**: Aggregate slot statuses with precedence MISSED > PENDING > TAKEN, and hide slots with no scheduled doses.
- **Rationale**: Matches spec UX and ensures missed attention is surfaced.
- **Alternatives considered**: Majority-based aggregation (confusing for adherence).

## Decision 4: Day detail ordering

- **Decision**: Order day detail items by slot then medication name (A–Z).
- **Rationale**: Predictable ordering across refreshes; fits slot-first mental model.
- **Alternatives considered**: Scheduled time order or generator order (less stable).

## Decision 5: Loading and error UX

- **Decision**: Full-screen blocking overlay for all history fetches; error text “読み込みに失敗しました。再試行してください。”; empty state text “患者を選択してください”.
- **Rationale**: Prevents partial interaction and provides clear recovery actions.
- **Alternatives considered**: Non-blocking loading (risk of stale interactions).
