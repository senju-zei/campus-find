# QA report: full pass after item photos and the staff web dashboard (2026-10-31)

Environment: owner's Windows PC. JDK 17.0.20.1 (Gradle), JDK 21.0.13 + Node 22.23.4 + Firebase CLI
(rules emulator on port 9010), Android emulator `emulator-5554` (1080x2400) running the
`-PuseEmulator=true` build against the local Auth (9099) and Database (9000) emulators.
Accounts: `clerk1@ms.bulsu.edu.ph` (Clara Cruz, clerk), `studentb@gmail.com` (Ben Reyes),
`studentc@gmail.com` (Carla Santos). Evidence is in `qa/evidence/`.

Code state tested: commit `8f3130c` plus uncommitted working-tree changes in `web/app.js`,
`web/index.html`, `web/styles.css` and `HANDOVER.md` (not made by QA). The web checks below cover
that working-tree version.

Result: **no failing test in any tier that was run.** Three low-severity findings, no blockers.

## Tier 0: Build and static

| ID | Test | Result | Evidence |
|---|---|---|---|
| T0-1 | `gradlew testDebugUnitTest lintDebug assembleDebug` | PASS | Exit 0, run twice; the second with `--rerun-tasks` (54 of 54 tasks executed, nothing cached). APK `app/build/outputs/apk/debug/campus-find.apk` built |
| T0-2 | Lint has no errors | PASS | 0 errors, 47 warnings: GradleDependency 16, UnusedResources 9, NewerVersionAvailable 5, Overdraw 4, NotifyDataSetChanged 3, UseKtx 2, UseCompoundDrawables 2, ExifInterface 2, AndroidGradlePluginVersion 2, PluralsCandidate 1, AlwaysShowAction 1. `evidence/lint-results-debug.txt` |
| T0-3 | `firebase/database.rules.json` parses as JSON | PASS | `JSON.parse` OK, 9344 bytes; one key `rules`; top-level nodes `users`, `items`, `itemSecrets`, `itemPhotos`, `claims` |
| T0-4 | Rules load in the emulator | PASS | Tier 2 ran against this file |
| T0-5 | `node --check web/app.js`, `node --check web/config.js` | PASS | Both exit 0 |

## Tier 1: Unit tests (`app/src/test/`)

| ID | Test | Result | Evidence |
|---|---|---|---|
| T1-1..T1-5 | Validators, ISBN, Open Library parsing, claim length, URL builders | PASS | `ValidatorsTest` 5/5, `OpenLibraryParserTest` 5/5 |
| T1-6 | Status transitions (incl. multi-claim) | PASS | `StatusTransitionsTest` 7/7 |
| T1-7 | WCAG contrast, light and dark | PASS | `ContrastTest` 1/1 |
| T1-8 | UI conformance scan | PASS | `UiConformanceTest` 9/9 |
| extra | Settled rules, search, "Might be yours" | PASS | `SettledAndSearchTest` 5/5 |

**32 tests, 0 failures, 0 skipped** (`app/build/test-results/testDebugUnitTest/*.xml`, 2026-10-31 19:40).

## Tier 2: Database rules (`qa/rules/`, emulator on port 9010)

| ID | Suite | Result |
|---|---|---|
| T2-1 | Users (R1-R3, R23) | PASS |
| T2-2 | Profile picture | PASS |
| T2-3 | Items (R4-R8, R17, R21, R22, R25-R27) | PASS |
| T2-4 | Item color, brand and heldAt | PASS |
| T2-5 | Hidden details (R9, R10) | PASS |
| T2-6 | Claims (R11-R16, R18-R20, R24; R14 now Allow) | PASS |
| T2-7 | Multiple claims and pickup code | PASS |
| T2-8 | Staff never claim; no self-review | PASS |
| T2-9 | Item photos | PASS |
| T2-10 | Deletes (staff Settled, History, student Profile) | PASS |

**62 tests in 10 suites, 62 pass, 0 fail.** `npm ci` then
`firebase.cmd emulators:exec --only database --project campus-find-86d58 "npm test"`, exit 0.
Per-test lines: `evidence/tier2_subtests.txt`.

## Web dashboard static checks (`web/`, no browser)

| ID | Test | Result | Evidence |
|---|---|---|---|
| W-1 | Syntax of `app.js`, `config.js` | PASS | `node --check` exit 0 |
| W-2 | Every `$('id')` in `app.js` exists in `index.html` | PASS | 23 ids, all found (appView, detail, dialog, dialogCancel, dialogContent, dialogOk, dialogTitle, email, greeting, greetingClose, greetingText, greetingTitle, list, logOut, loginButton, loginError, loginForm, loginView, password, toast, who, whoName, whoRole) |
| W-3 | User text never goes through HTML parsing | PASS | No `innerHTML`, `outerHTML`, `insertAdjacentHTML` or `document.write` in `web/`. All content is built by `h()` (createElement, `append` of strings, `setAttribute`) and `textContent`. Photo `src` is `data:image/jpeg;base64,` + DB string through `setAttribute`, which cannot break out of the attribute |
| W-4 | Approve matches `ClaimRepository.approve` and the rules | PASS | Same paths and fields: claim `status`=APPROVED, `reviewedBy`, `pickupCode` (6 digits, matches `^[0-9]{6}$`); item `status`=RETURNED, `updatedAt`; other pending claims `status`=REJECTED, `rejectReason`, `reviewedBy`. Same reason text as `reason_other_claim_approved` ("Another claim for this item was approved.") |
| W-5 | Reject matches `ClaimRepository.reject` | PASS | Claim `status`, `rejectReason` (trimmed, 1-200, rules 1-200), `reviewedBy`; item `status` = CLAIM_PENDING if others pending, else TURNED_IN if `turnedInAt`, else OPEN; `updatedAt` |
| W-6 | Mark turned in matches `ItemRepository.markTurnedIn` | PASS | `status`=TURNED_IN, `turnedInAt`, `updatedAt` |
| W-7 | Delete settled matches `ItemRepository.deletePermanently` | PASS | `items/{id}`, `itemSecrets/{id}`, `itemPhotos/{id}`, `claims/{id}` set to null in one update |
| W-8 | Delete history matches `ClaimRepository.deleteClaims` | PASS | `claims/{itemId}/{claimant}` = null |
| W-9 | Faculty change status matches `ItemRepository.overrideStatus` | PASS | `status`, `updatedAt` |
| W-10 | Non-staff accounts are signed out | PASS (code read) | `onAuthStateChanged` signs out unless `role` is clerk or faculty |

W-4 to W-10 are code comparisons, not executed writes. The rules accepting these exact updates is
covered by tier 2 (same multi-path shapes).

## Tiers 3-7: Android end-to-end on the emulator (adb)

| ID | Test | Result | Evidence |
|---|---|---|---|
| E-1 | Log out asks for confirmation; Cancel keeps the session; Log out returns to Welcome | PASS | `e02_logout_confirm.png` ("Log out?" / "You will need your email and password to log in again."), `e03_welcome.png` |
| E-2 | Welcome "How it works" strip | PASS | `e03_welcome.png` (3 steps) |
| E-3 | Student (Ben) posts a FOUND item with category, color, location, room, description and a hidden detail | PASS | `e07_post_filled.png`; Found notice "Please bring the item to the CS Office..." shown |
| E-4 | New item on Feed and the Found count goes up | PASS | Feed "Found (4)" before, "Found (5)" after, "QA blue umbrella · Other · Canteen · Just now · Open" (`e08_after_post.png`) |
| E-5 | Another student (Carla) sees no hidden detail and no photos | PASS | `e10_claimant_detail.png`; "QA7" not found anywhere in the UI dump; no photos section. The item had no photos, so photo privacy on screen was not exercised (covered by T2-9) |
| E-6 | Claim text under 10 characters is refused | PASS | "Too short" (9) shows "Enter at least 10 characters." (`e11_claim_short.png`) |
| E-7 | Claim submitted | PASS | Item becomes "Claim pending", "Your claim · Pending · The CS Office is reviewing your claim." (`e12_claim_submitted.png`) |
| E-8 | Clerk sees the staff badge count | PASS | Badge "1" on the staff icon (`e13_clerk_feed_badge.png`) |
| E-9 | Staff dashboard > Claims lists the claim beside the hidden detail | PASS | "Claims (1)", hidden detail "Handle has initials QA7 scratched" beside "Blue umbrella with a wooden handle" (`e14_staff_claims.png`, `e15_review.png`) |
| E-10 | Reject with an empty reason is refused | PASS | "Enter a reason, up to 200 characters." |
| E-11 | Reject with a reason; item returns to OPEN | PASS | Claims empty, "To turn in (1)" (`e16_after_reject.png`) |
| E-12 | Student sees the reason in Profile > Claims | PASS | "Rejected · Reason: Handle is plastic not wood" + "Claim again" (`e17_carla_claims_rejected.png`) |
| E-13 | Claim again | PASS | Back to "Pending" (`e18_claim_again.png`) |
| E-14 | Clerk approves | PASS | Claims empty, "Settled (2)", umbrella "Returned" (`e22_approve_confirm.png`, `e23_staff_settled.png`) |
| E-15 | Student sees the pickup code (Profile and Item detail) | PASS | "Your pickup code: 144086" in Profile > Claims and on Item detail (`e24_carla_pickup_code.png`, `e25_carla_item_code.png`) |
| E-16 | Profile picture can be chosen from the gallery | PASS | Avatar tap opens the system photo picker (`e19_picker.png`); picked image shows on Profile (`e20_avatar_set.png`) and is still there after log out and log in (`e24_carla_pickup_code.png`) |
| E-17 | "Remove photo" offered once a photo is set | PASS | Dialog "Profile picture: Choose a photo / Remove photo" (`e21_avatar_options.png`). Removal itself not tapped |
| E-18 | Staff never see "Claim item" | PASS | Clerk on a TURNED_IN item: no Claim button (`e26_clerk_no_claim.png`) |
| E-19 | No app crash during the run | PASS | `adb logcat -d -b crash` empty |

## Defects and findings

No functional defects found. Findings, all low severity:

1. **Low: Android approves a claim without a confirmation step; the web dashboard asks first.**
   One tap on "Approve claim" writes APPROVED + pickup code, sets the item RETURNED and rejects every
   other pending claim. It cannot be undone except by a faculty override. The web version shows
   "Approve claim? ... N other claim(s) will be rejected." Observed: tap "Approve claim" in Review
   claim, screen returned straight to the dashboard with "No claims to review." Code:
   `ReviewClaimFragment.kt:40`
   `binding.approveButton.setOnClickListener { viewModel.approve(getString(R.string.reason_other_claim_approved)) }`.
   Likely file: `app/src/main/java/ph/edu/bulsu/campusfind/ui/staff/ReviewClaimFragment.kt`.
   Owner decision whether this is wanted.
2. **Low: lint `ExifInterface` warning in the new photo code (minSdk 24).**
   `ProfilePhoto.kt:8: Warning: Avoid using android.media.ExifInterface; use androidx.exifinterface.media.ExifInterface instead [ExifInterface]`
   (also line 85). The platform class has known bugs on older Android versions. Likely file:
   `app/src/main/java/ph/edu/bulsu/campusfind/ui/common/ProfilePhoto.kt` (plus the
   `androidx.exifinterface` dependency).
3. **Low (accessibility): staff badge reads "1 new notification".** The badge's content
   description is Material's default (`desc='1 new notification' id=action_staff_dashboard`), so
   TalkBack does not say "Staff dashboard, 1 claim to review". Likely file:
   `app/src/main/java/ph/edu/bulsu/campusfind/ui/feed/FeedFragment.kt` (around line 205, where
   `BadgeDrawable.create` is called); set the badge's content description quantity string.

Observations (not defects): Item detail does not show the hidden detail even to the poster (only
staff on Review claim and the staff dashboard); lint lists 9 unused resources (for example
`title_my_activity`, `label_hidden_detail`, `ic_history`) left from earlier screens.

## NOT RUN

- **Web dashboard in a browser** (log in, Claims, approve, reject, turn in, pickup check, settled
  and history delete, faculty change status, dialogs): no browser available to QA. Static checks
  only (W-1 to W-10).
- **Item photos on the phone** (Take photo with the camera, Choose photo, Found photos hidden from
  other students on screen, tap to enlarge, photos on Review claim): not exercised; the test item
  was posted without photos. Rules for photos pass in T2-9.
- **Multiple claims on one item in the UI** (two students claim, approve one, the other sees
  "Another claim for this item was approved."): only one claimant was used. Covered by T2-7.
- **Faculty role screens** (Change status): no faculty account in the Auth emulator.
- **Settled / History multi-select delete and student "remove from my Profile"**: not tapped, to
  keep the shared emulator data intact. Covered by T2-10.
- **Remove profile photo**: option seen, not tapped.
- **Espresso tests in `app/src/androidTest/`** (planned tiers 3-7 automation): none exist yet; the
  flow above was driven by hand through adb.

## Left for the owner (manual)

- Real email delivery: verification mail from `noreply@campus-find-86d58.firebaseapp.com` reaching
  Gmail and the BulSU inbox (check spam / quarantine).
- Real phones: the flow above on at least one physical Android phone against real Firebase,
  including the camera for item photos and the photo picker on an older Android version.
- Live staff dashboard at https://senju-zei.github.io/campus-find/ with a real staff password:
  log in, approve, reject, pickup check, delete; a student account must be refused.
- Confirm the rules in the Firebase console match `firebase/database.rules.json` (HANDOVER asks to
  publish after the profile picture and item photo changes).
- The uncommitted changes in `web/` and `HANDOVER.md` were tested as they are in the working tree;
  commit them before relying on this report for the deployed site.

## Follow-up (builder, after this report)

- Finding 1 fixed: Review claim now asks "Approve claim?" first, naming the student and how many
  other pending claims will be rejected (`ReviewClaimFragment.confirmApprove`). Checked on the
  emulator: confirming approves (claim APPROVED with a pickup code, item RETURNED).
- Finding 3 fixed: the staff badge is read as "1 claim to review" (`badge_pending_claims` plural via
  `setContentDescriptionQuantityStringsResource`). Checked with a uiautomator dump.
- Finding 2 left as is: it is a lint warning only. Android 9 and later decode photos with
  ImageDecoder, which rotates them itself; the old ExifInterface path runs only on Android 7 and 8.
- Web dashboard, after this report: log-in page with a welcome panel, a greeting after log-in and a
  Lost and found tab, tested in the desktop app's browser against the emulators.
