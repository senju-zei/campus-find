# Campus Find – Handover

Read this first in a new session, together with `Campus_Find.md` (the plan, in the owner's Downloads).

## Where things are
- Project: `C:\Users\emman\CAMPUS FIND` (Kotlin, Views, single activity, package `ph.edu.bulsu.campusfind`).
- GitHub: `senju-zei/campus-find` (public). Pushing to `main` builds and replaces the APK at
  https://github.com/senju-zei/campus-find/releases/download/latest/campus-find.apk
  Git on this PC pushes as `EmmanIvan` (collaborator); commits are signed `senju-zei`.
- Firebase: project `campus-find-86d58` (Google account zen65877@gmail.com), Auth Email/Password,
  Realtime Database in **us-central1**. `app/google-services.json` is local only (gitignored) and is in
  the repo secret `GOOGLE_SERVICES_JSON`.
- Rules: `firebase/database.rules.json`. **After changing it, paste it into the Firebase console
  (Realtime Database → Rules → Publish)**, or the real app keeps the old rules.
- Staff: set `users/{uid}/role` to `clerk` or `faculty` in the console; the user logs out and in.

## Tools on this PC
- JDK 17 (Gradle): `C:\Users\emman\tools\jdk\jdk-17.0.20.1+1`
- JDK 21 + Node 22 + Firebase CLI (emulators): `C:\Users\emman\tools\jdk21\...`, `C:\Users\emman\tools\node-v22.23.4-win-x64`, `C:\Users\emman\tools\npm-global\firebase.cmd`
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`, emulator AVD `campusfind_api35`.
- Build like CI: `gradlew testDebugUnitTest lintDebug assembleDebug` (JAVA_HOME = JDK 17).
- Emulator build: add `-PuseEmulator=true` (Auth 9099, Database 9000 on 10.0.2.2), then
  `firebase emulators:start --only auth,database --project campus-find-86d58` (JAVA_HOME = JDK 21).

## Done
- Steps 3–6 of the plan: skeleton, theme, sign-up/login with verification + token refresh, Feed,
  Post item (ISBN look-up, Open Library cover, DiceBear badge), Item detail, Claim, My activity,
  Staff dashboard, Review claim (approve / reject with reason), Mark turned in, faculty Change status.
- Welcome landing page with an animated logo (magnifying glass with an eye), at the owner's request.
- Database reads use a one-time listener with a timeout (`readOnce`), writes use `awaitWrite`
  (DatabaseReference.get() hung in testing).
- Tested end to end on the Android emulator against the Firebase emulators. Not yet: step 7 tests.

## Multiple claims + pickup code: DONE
- Rules updated and **published** in the console (file and console match, 7201 chars).
- Approve = claim APPROVED + `pickupCode` + other pending claims REJECTED + item RETURNED, one update.
- Reject keeps the item CLAIM_PENDING while other claims are pending.
- Claimant sees "Your pickup code" on Item detail and My activity; staff see "Approved for X · pickup code" on Item detail.
- Verified with REST rules checks in the emulator (2 claimants + clerk). Not yet clicked through on a phone.

## Category icons + color + brand: DONE
- Items without a book cover show a Material Symbols category icon (`ic_cat_*.xml`) tinted with the
  item color (`ItemUi.loadPicture`); the DiceBear badge is no longer used.
- New optional item fields `color` (key from `Constants.COLORS`) and `brand` (<= 60); rules updated and
  **published** (console matches the file, 7431 chars).
- Post item has color chips and a Brand or model field; Item detail shows Color, Brand, and
  "Ask to see it at the CS Office" for TURNED_IN items.

## Owner decisions
- Keep current library versions until after the demo.
- **Multiple claims per item** (changes the plan): any student except the finder can claim while the
  item is OPEN, TURNED_IN or CLAIM_PENDING. Approving one claim sets the item RETURNED and rejects every
  other claim on it in the same update ("Another claim was approved.").
- **Private pickup code**: on approval a random 6-digit code is stored on the approved claim
  (`pickupCode`). Only that claimant and staff can read it (claims are already private).

## Batch 1: DONE
- **Clerks and faculty cannot claim at all** (owner decision): the claim rule and the CLAIM_PENDING
  status rule require role `student`; Item detail hides "Claim item" for staff. As a second guard the
  staff branch also has `$claimUid !== auth.uid`, and Review claim hides Approve/Reject on an own claim.
  Rules published (7675 chars).
- Profile page = My activity with a header (initials, name, email, role chip, counts) and Log out.
  The Feed icon is now a person icon; Log out left the Feed menu.
- Post item (Found): "Please bring the item to the CS Office" notice + "I left it somewhere else" ->
  "Where is it now?" (new item field `heldAt`, <= 100). Shown as "Now at: ..." in lists and
  "Where it is now" on Item detail while the item is OPEN. Rules published (7545 chars).

## Next: one batch per new session
Prompt: "Read HANDOVER.md. Do batch N. Ask me before pushing."
- **Batch 2: DONE.** `domain/Settled.kt` (3-day Feed hide, 30-day delete, settled time = updatedAt or
  createdAt). Closing a LOST item now stamps `updatedAt` (rules allow the poster this). Staff dashboard
  has a third tab "Settled" with "Delete items settled over 30 days ago"; Item detail shows "Delete
  permanently" to staff once deletable. Delete = items/{id}, itemSecrets/{id}, claims/{id} set to null in
  one update (rules allow staff deletes only). Release step in build.yml retries the delete 3 times.
  Rules published (8212 chars). Students' myClaims pointers to deleted items stay but show nothing.
- **Batch 3: Search and filter + match suggestions + swipe-down refresh.** Feed search box and
  chips (category, color; text matches title, brand, description). After posting a Lost item, show
  "Might be yours": Found items with same category (and color/location) from the last 14 days.
  SwipeRefreshLayout on Feed and Profile (re-subscribe; shows "No connection" if offline). No rules change.
- **Batch 4: Polish + step 7 tests.** Tab counts, badge on the staff icon for pending claims,
  how-it-works strip on Welcome; reminder in Profile for finders whose Found item is still OPEN.
  Then the QA agent (`.claude/agents/qa-tester.md`), unit tests (validators, ISBN, transitions,
  contrast, UI scan) and rules tests R1-R27 + the new multi-claim, pickup code, self-review, color and
  heldAt rules in `qa/rules/`; add tier 2 to build.yml.
- Later: real item photos (Lost public, Found staff only), notifications, auto-close.

## Watch out for
- Verification emails come from `noreply@campus-find-86d58.firebaseapp.com` and may land in spam or
  the BulSU quarantine.
- Material 1.12 has no `colorSurfaceTint`; `elevationOverlayEnabled=false` is used instead.
- `Task<Void>.await()` returns null on success; `awaitWrite` returns Unit to tell success from timeout.
