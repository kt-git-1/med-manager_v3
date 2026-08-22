# Guided caregiver onboarding

## Decision

The Android caregiver first-run experience combines explanation and real setup inside one ten-page sequence:

1. Pages 1–6 explain the main product areas with isolated samples.
2. Page 7 creates the first patient by entering a display name.
3. Page 8 issues, reviews and shares the real six-digit linking code.
4. Page 9 registers the first medication, schedule and inventory.
5. Page 10 requests notification permission.

This is an intentional product improvement approved during Staging device QA. It extends the published iOS 1.0.6 flow; it must not be described as strict iOS parity until iOS adopts the same sequence.

## State and transition contract

- Pages 1–6 remain isolated samples and never mutate Staging/Production data.
- Page 7 opens the existing patient-creation sheet only after an explicit tap. An authoritative success advances to page 8; an existing patient skips duplicate creation.
- Page 8 calls the code-issue API only after an explicit tap. The code sheet exposes the existing copy/share actions; dismissing a successfully issued sheet advances to page 9.
- Page 9 opens the existing medication editor only after an explicit tap. An authoritative save advances to page 10.
- Page 10 marks the tutorial as seen and requests notification permission only after `通知をオンにする`.
- Selecting `あとで設定する` marks the tutorial as seen and performs no additional mutation. Data successfully created on earlier pages remains valid.
- Closing an operational surface returns to the same tutorial page so the user can retry or skip.
- Failed patient, code, or medication mutations remain on their normal retryable screen and never advance the sequence.

## Safety and privacy

- No patient name, linking code, medication name, identifier, or schedule is sent to Analytics.
- The flow reuses the production repositories and validation rules; it does not create a second onboarding API path.
- Patient, code, and medication creation require separate explicit user actions.
- Tutorial sample content remains non-interactive and is removed while the full-screen medication editor is active.

## Acceptance checks

- Step 7/10 displays the patient-registration sample until its explicit primary action opens the real create sheet.
- Patient success advances to the page 8 real code issue action.
- Code issue failure does not open medication registration.
- Dismissing a successfully issued code advances to page 9; its primary action opens the real medication editor.
- Medication success advances to page 10; closing without saving returns to page 9.
- Back/close never deletes successful prior work.
- Existing tutorial reachability, dark mode, 200% font scale and TalkBack contracts remain valid.
