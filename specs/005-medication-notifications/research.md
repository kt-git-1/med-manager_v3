# Research: Medication Notifications

## Decision 1: Local notification scheduling strategy

**Decision**: Use device-local scheduling via UNUserNotificationCenter with stable identifiers (`notif:{YYYY-MM-DD}:{slot}:{1|2}`) and reschedule on app launch/foreground/settings changes.  
**Rationale**: Works within Vercel free constraints (no server cron), enables deterministic cancel/reschedule behavior, and aligns with iOS best practices for local reminders.  
**Alternatives considered**: Server-side cron + push notifications (rejected due to hosting constraints); background fetch scheduling (unreliable timing for exact slot reminders).

## Decision 2: Handling notification permission states

**Decision**: If permission is denied, show guidance in Settings and disable toggles; master toggle defaults OFF on first use.  
**Rationale**: Matches consent-first UX, avoids scheduling failures, and makes recovery path explicit.  
**Alternatives considered**: Leave toggles enabled and show errors on schedule (poor UX, more support load).

## Decision 3: Reminder while app open

**Decision**: Show an in-app banner when a reminder fires while the app is open; if on Today, highlight the slot.  
**Rationale**: Avoids redundant system notifications and still provides a cue and navigation context.  
**Alternatives considered**: System notification even while foregrounded (disruptive); no visual cue (missed reminder).

## Decision 4: Caregiver in-app banners

**Decision**: Use Supabase Realtime subscriptions with RLS-scoped dose event rows; queue banners sequentially for ~3 seconds.  
**Rationale**: Works without push; ensures caregivers only see linked patient events; sequential banners preserve visibility of multiple events.  
**Alternatives considered**: Push notifications (out of scope); polling (worse UX and battery).
