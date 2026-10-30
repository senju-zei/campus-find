# QA report – batch 4 (2026-10-31)

Environment: owner's Windows PC. JDK 17 (Gradle), JDK 21 + Firebase CLI 15.34.2 (emulator),
Android emulator `campusfind_api35`.

## Tier 0: Build and static

| ID | Test | Result | Evidence |
|---|---|---|---|
| T0-1 | `assembleDebug` | PASS | `gradlew testDebugUnitTest lintDebug assembleDebug` exit 0 |
| T0-2 | Lint has no errors | PASS | 45 warnings (dependency versions, unused resources, overdraw), 0 errors |
| T0-3 | Rules parse and load in the emulator | PASS | Tier 2 ran against `firebase/database.rules.json` |

## Tier 1: Unit tests (`app/src/test/`)

| ID | Test | Result |
|---|---|---|
| T1-1 | Email domain validator | PASS |
| T1-2 | ISBN-10 / ISBN-13 checksums, hyphens, X | PASS |
| T1-3 | Open Library parsing (found, empty, no cover, no authors) | PASS |
| T1-4 | Claim text 9 / 10 / 500 / 501 | PASS |
| T1-5 | Badge and cover URL builders | PASS |
| T1-6 | Status transitions (section 3 + multi-claim) | PASS |
| T1-7 | WCAG contrast, light and dark, 12 pairs each | PASS (all at least 4.5:1) |
| T1-8 | UI conformance scan | PASS |
| extra | Settled rules, student claim removal, search, "Might be yours" | PASS |

32 tests, 0 failures.

## Tier 2: Database rules (`qa/rules/`, emulator)

53 tests, 0 failures. Covers R1-R27 and the later owner rules: multiple claims (R14 is now
Allow), approve-one-reject-others, pickup code format and privacy, staff cannot claim or self-review,
color / brand / heldAt, staff deletes, student removing their own `myClaims` pointer.

## Manual check on the emulator (batch 4 UI)

Student account: Feed top bar shows "Campus Find" with the logo after "Find"; tab counts
"Found (1)", "Lost (3)", "Posts (3)", "Claims (1)". Long-press on an approved claim opens
"1 selected"; the trash icon shows the remove dialog with the pickup-code warning. Cancelled, so
the removal itself was checked by the rules tests only.

## NOT RUN

- Staff screens on the emulator (badge on the staff icon, staff tab counts, centered "Settled"
  heading, returned-item delete warning): no clerk password for the emulator account at hand.
- Welcome "How it works" strip: the emulator stayed signed in.
- Tiers 3-7 (Espresso, two-session flows): not written yet.

## Left for the owner

Section 12 (M1-M5), and a look at the staff screens on a phone.
