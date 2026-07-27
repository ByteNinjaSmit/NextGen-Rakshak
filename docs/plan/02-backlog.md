# Master Backlog — Every Remaining Task

**80 tasks.** Every one has an owner, a week, a priority, an effort estimate and
an acceptance criterion. Nothing here is "look into X" — if a task cannot be
declared done by a specific observable result, it does not belong on this list.

**Owners:** `SB` Bankar Smitraj (09) · `TB` Bhakare Tanishka (11) ·
`VD` Dhadge Vedant (34) · `AN` Narkhede Atharva (94)

**Priority:** `P0` blocks the submission · `P1` needed for a credible project ·
`P2` improves the grade · `P3` nice to have, drop without regret

---

## A. Deployment & Configuration — 8 tasks

Everything downstream is blocked on this section. Do it first, do it in Week 4,
do not let it slip.

| ID | Task | Owner | Week | Pri | Est | Acceptance criterion |
|---|---|---|---|---|---|---|
| **DEP-01** | Deploy Cloud Functions, Firestore rules, Storage rules and indexes | TB | 4 | P0 | 3h | `firebase deploy --only functions,firestore,storage` succeeds; all 7 functions listed in the console |
| **DEP-02** | Verify `tfjs-node` loads in the Functions runtime | TB | 4 | P0 | 2h | Create one alert; `onAlertCreated` logs `Embedding written … dims: 128`. If the binding fails, fall back to running the model in a container or precomputing the embedding client-side — decide same day |
| **DEP-03** | Confirm the SavedModel ships inside the deploy bundle | TB | 4 | P0 | 1h | `functions/model/savedmodel/` present in the uploaded artefact; deploy size under the limit; embedding succeeds on a cold start |
| **DEP-04** | Host the police kiosk (Firebase Hosting or Vercel) | AN | 4 | P0 | 3h | Public HTTPS URL loads the login screen; `next build` clean; env vars set in the host |
| **DEP-05** | Create `allowedOfficers/{email}` for two officers and verify the claim flow | VD | 4 | P0 | 2h | Allow-listed account reaches the dashboard; a non-listed Google account is signed straight back out with the explanation |
| **DEP-06** | Enable Email/Password and Anonymous providers in the Firebase console | VD | 4 | P0 | 1h | Both providers show Enabled; volunteer app email sign-up, sign-in and guest all reach Home on a device |
| **DEP-07** | Register the release SHA-1 and produce a signed release APK | SB | 6 | P1 | 3h | Release APK installs on a phone with no debug tooling; Google sign-in works from it |
| **DEP-08** | Generate model artefacts on all four team machines | SB | 4 | P1 | 2h | Each member runs the three scripts and gets `verify_parity.py` cosine ≥ 0.99 |

---

## B. Verification & Measurement — 16 tasks

This section is Objective 8. It is what converts "we built it" into "we validated
it", and it is where seven unproven NFRs get their numbers. Record **every**
result in `docs/plan/measurements.md` as you go — do not plan to reconstruct
numbers in Week 8.

| ID | Task | Owner | Week | Pri | Est | Acceptance criterion |
|---|---|---|---|---|---|---|
| **VER-01** | Run the app on ≥2 physical Android phones, all four screens | AN | 4 | P0 | 3h | Screenshots of Login, Home, Scan, Match from two different screen sizes; no crash, no layout clipping |
| **VER-02** | Measure per-face inference time on device (NFR-03 <200 ms) | SB | 5 | P0 | 4h | ≥100 timed inferences per phone; mean, p50, p95 recorded; verdict stated against 200 ms |
| **VER-03** | End-to-end online path: alert → FCM → scan → match → kiosk | TB | 4 | P0 | 4h | Match row appears on the kiosk within 2 s of confirmation, with correct GPS, photo and confidence |
| **VER-04** | Verify the 2 km geofence | TB | 5 | P1 | 3h | Volunteer with `lastLocation` inside 2 km is pushed; one 5 km away is not; one with no location **is** pushed (fail-open) |
| **VER-05** | Measure recognition accuracy on **children's** faces (NFR-02 ≥90%) — *direct continuation of the Week 3 36-pair study* | AN | 5 | P0 | 6h | ≥30 same-person and ≥50 different-person pairs from a consented or public child-face set; ROC or score table; verdict against 90% and against threshold 0.55 |
| **VER-06** | Measure ML Kit detection accuracy (NFR-01 ≥95% frontal) | AN | 5 | P1 | 3h | ≥100 frontal faces counted detected/missed; percentage recorded |
| **VER-07** | Degradation study: distance, motion blur, low light | SB | 6 | P2 | 4h | Cosine score table at 1 m / 3 m / 5 m and in bright / dim light; stated limits of the system |
| **VER-08** | **Multi-device mesh trial** — 3+ phones, all offline | TB | 5 | P0 | 6h | With phone A only online: alert reaches phone C via B; TTL decrements as logged; a duplicate arriving twice is relayed once; delivery time measured against NFR-05 (30 s) |
| **VER-09** | Load test: 50 concurrent active alerts (NFR-07, FR-11) | SB | 6 | P1 | 3h | 50 alerts seeded; scan frame time and memory before vs after; degradation quantified |
| **VER-10** | Offline match queue: Room + WorkManager | VD | 5 | P1 | 3h | Airplane mode → confirm a match → re-enable network → row appears on the kiosk with the original timestamp |
| **VER-11** | Alert expiry sweep (FR-12) | VD | 5 | P1 | 2h | Backdate an alert past 8 h; within 30 min it flips to `resolved`, embedding cleared, photo gone from Storage |
| **VER-12** | Privacy evidence for NFR-08 | VD | 6 | P1 | 3h | Network capture or Firestore/Storage audit during a scan session proving **no** bystander image or embedding leaves the phone. This is the project's headline claim — evidence it, don't assert it |
| **VER-13** | Battery impact measurement (NFR-09) | AN | 6 | P2 | 3h | Battery percentage over a 30-minute scan session vs 30 minutes idle-with-alert; camera confirmed released when scanning stops |
| **VER-14** | Scan throughput (NFR-06, 200+ faces/hour) | AN | 6 | P2 | 2h | Faces processed per minute in a realistic walk-through, extrapolated with the method stated |
| **VER-15** | Firestore rules negative-test suite (emulator) | VD | 5 | P1 | 4h | Automated tests prove: volunteer cannot create an alert; nobody can delete; `allowedOfficers` is unreadable by any client; a volunteer can only write their own doc |
| **VER-16** | Full-system regression pass before code freeze | All | 7 | P0 | 6h | Every test case in the test-case document executed once against the release build, pass/fail recorded |

---

## C. Feature Gaps — 13 tasks

| ID | Task | Owner | Week | Pri | Est | Acceptance criterion |
|---|---|---|---|---|---|---|
| **GAP-01** | Implement `onPayloadTransferUpdate` — log and surface failed transfers | TB | 5 | P1 | 3h | A failed transfer is logged with endpoint and status; a `FAILURE` triggers one retry; unit or manual evidence |
| **GAP-02** | Mesh payload signing (synopsis §5.1.4) — **or** formally descope | TB | 6 | P2 | 5h | Either an HMAC over the payload verified on receipt with a tampered packet rejected, **or** a written descope with the reason in the final report |
| **GAP-03** | Notification tap opens the Scan screen directly (synopsis §7 step 5) | AN | 5 | P1 | 2h | Tapping an alert notification from a locked phone lands on Scan with that alert active |
| **GAP-04** | Dashboard map of recent match locations (synopsis §6.1.2) | AN | 6 | P2 | 4h | Dashboard renders pins for the last N matches; degrades gracefully with zero matches |
| **GAP-05** | Kiosk "no connection" banner + retry | AN | 6 | P3 | 2h | Firestore unreachable shows a banner instead of an empty page |
| **GAP-06** | **Send a low-resolution thumbnail over the mesh** | TB+AN | 5 | P1 | 5h | ≤8 KB JPEG travels in the alert packet; with no internet the match dialog renders the parent's photo. Without this, FR-07 silently degrades in exactly the offline scenario the project is built for |
| **GAP-07** | **Decide the Raspberry Pi node: build or descope** | SB | 4 | P0 | 1h | A written decision in this repo. Recommendation: **descope**, documented as an optional extension with a design sketch — it is FR-13 *Low (Optional)* and Week 7 belongs to the report |
| **GAP-08** | (If GAP-07 says build) Minimal Pi node: poll alerts, detect, match, POST | SB | 6 | P3 | 12h | Pi detects a face at a gate and posts a match visible on the kiosk |
| **GAP-09** | Correct `minSdk` drift — align the synopsis to API 24 | SB | 6 | P1 | 1h | Report and synopsis state API 24 with the reason (CameraX / LiteRT / Credential Manager) |
| **GAP-10** | Document the kiosk-cannot-mesh constraint | TB | 6 | P1 | 1h | Report states alerts enter the mesh via the first online volunteer phone, with the platform reason. Do not leave the synopsis diagram uncorrected |
| **GAP-11** | Map pin per match on the kiosk matches page | AN | 6 | P2 | 3h | Each match row links to a pin; hovering or clicking shows it on the dashboard map |
| **GAP-12** | Multi-sighting handling: same child seen by 2 volunteers — **stretch, not scheduled** | AN | 6 | P3 | 3h | Kiosk groups matches by `alertId` and shows the most recent location first |
| **GAP-13** | Graceful "embedding not ready" state | TB | 5 | P2 | 2h | An alert whose embedding has not yet been computed shows as "preparing" on the app rather than silently never matching |

---

## D. Engineering Quality — 10 tasks

| ID | Task | Owner | Week | Pri | Est | Acceptance criterion |
|---|---|---|---|---|---|---|
| **QA-01** | GitHub Actions CI | SB | 4 | P1 | 3h | On push: `gradlew testDebugUnitTest` + `assembleDebug`, `tsc --noEmit` for web and functions, `next lint`. Green badge in the README |
| **QA-02** | Web portal tests | AN | 6 | P2 | 4h | Unit tests for `lib/firestore.ts` helpers; one Playwright smoke test covering login → create alert → see it listed |
| **QA-03** | Android Compose UI tests | SB | 6 | P2 | 4h | Instrumented tests for Login, Home (empty + populated), and the match dialog |
| **QA-04** | Extend Android unit tests | SB | 5 | P1 | 4h | Add tests for `FacePreprocessor` crop geometry, mesh TTL/relay/dup logic, and the Firestore+mesh alert merge. Target ≥25 tests total |
| **QA-05** | Cloud Functions tests on the emulator | TB | 6 | P2 | 4h | `expireAlerts`, `onAlertResolved` and the geofence filter each covered by an emulator test |
| **QA-06** | Adopt a branch workflow | SB | 4 | P2 | 1h | `main` protected; feature branches + PR for the rest of the project. Also demonstrates the "standard feature-branch workflow" the synopsis claims |
| **QA-07** | Commit the 4 untracked Week-3 member documents | SB | 4 | P0 | 15m | `git status` clean |
| **QA-08** | Release build config: signing, R8, shrink | SB | 6 | P1 | 3h | Signed release APK under 30 MB, installs and runs; model asset intact after shrinking |
| **QA-09** | Add Crashlytics | VD | 6 | P3 | 2h | A forced test crash appears in the console |
| **QA-10** | Third-party licence inventory | VD | 7 | P2 | 2h | Table of every dependency and its licence, in the report appendix |

---

## E. Documentation & Academic Deliverables — 25 tasks

This is the largest section by count and by risk. A working system with a thin
report scores worse than an average system with an excellent one.

| ID | Task | Owner | Week | Pri | Est | Acceptance criterion |
|---|---|---|---|---|---|---|
| **DOC-01** | Fill the blank week date ranges in Week 2 and Week 3 documents | AN | 4 | P0 | 30m | No `____________` remains anywhere in `docs/` |
| **DOC-02** | Write or reconstruct the Week 1 report (literature survey) | AN | 4 | P1 | 4h | `docs/week-1/` exists with the same 4-track structure, drawn from synopsis §4 |
| **DOC-03** | Week 4 report — README + 4 member tracks | All | 4 | P0 | 5h | Same format as Week 3; every claim tied to something that was run |
| **DOC-04** | Week 5 report — README + 4 member tracks | All | 5 | P0 | 5h | ” |
| **DOC-05** | Week 6 report — README + 4 member tracks | All | 6 | P0 | 5h | ” |
| **DOC-06** | Week 7 report — README + 4 member tracks | All | 7 | P0 | 5h | ” |
| **DOC-07** | Week 8 report — README + 4 member tracks | All | 8 | P0 | 5h | ” |
| **DOC-08** | `measurements.md` — running log of every measured number | SB | 5 | P0 | 2h | Every NFR has a row: target, measured, method, date, device |
| **DOC-09** | Test plan document | VD | 5 | P0 | 4h | Scope, strategy, environments, entry/exit criteria, defect severity definitions |
| **DOC-10** | Test case document — ≥60 cases with results | VD | 6 | P0 | 8h | ID, precondition, steps, expected, actual, pass/fail, evidence link. Covers every FR and NFR |
| **DOC-11a** | Final report ch. 1: Introduction — **started early to unload Week 7** | AN | 5 | P0 | 4h | From synopsis §1–3, expanded |
| **DOC-11b** | Final report ch. 2: Literature Survey | VD | 5 | P0 | 4h | From synopsis §4, expanded, with citations |
| **DOC-12** | Final report ch. 3: Requirement Analysis | VD | 6 | P0 | 5h | FR/NFR tables **as delivered**, with the eight synopsis corrections integrated |
| **DOC-13** | Final report ch. 4: System Design | TB | 6 | P0 | 8h | Architecture, DFDs, ER/schema, sequence diagrams, mesh protocol, UML class diagram |
| **DOC-14** | Final report ch. 5: Implementation | SB | 7 | P0 | 8h | Module-wise, with key code excerpts and the design decisions behind them |
| **DOC-15** | Final report ch. 6: Testing | VD | 7 | P0 | 6h | Strategy, test cases, defect log — including the 7 Week-3 defects, which are excellent material |
| **DOC-16** | Final report ch. 7: Results & Analysis | SB | 7 | P0 | 8h | Every measurement with a chart: accuracy separation, inference latency, mesh delivery time, geofence, load |
| **DOC-17** | Final report ch. 8: Conclusion + Future Scope | AN | 7 | P0 | 4h | Honest about limitations; Pi node, signing, and thumbnails as future work if descoped |
| **DOC-18** | Comparison table vs TrackChild / Khoya-Paya / ReUnite / Garuda / Maha Kumbh kendras / AMBER | AN | 7 | P1 | 3h | Feature matrix with NextGen Rakshak's row justified by measurements, not claims |
| **DOC-19** | Final figures: architecture, system flow, DFD, ER, sequence, mesh topology | AN | 7 | P1 | 6h | Vector or high-DPI, consistent style, numbered and captioned |
| **DOC-20** | Deployment / user manual | TB | 7 | P1 | 4h | Officer's guide, volunteer's guide, and an admin setup runbook |
| **DOC-21** | IEEE-format paper draft | SB | 7 | P2 | 8h | 6–8 pages. **Confirm in Week 4 whether your college requires this** — it is a large, hard-deadline item if so |
| **DOC-22** | Plagiarism check on the final report | VD | 8 | P1 | 2h | Similarity below your institution's threshold; report retained |
| **DOC-23** | Final presentation slides (20–25) | AN | 8 | P0 | 6h | Problem, gap, architecture, demo, results, conclusion. Every result slide shows a measured number |
| **DOC-24** | Update root `README.md` and `CLAUDE.md` to the delivered state | SB | 8 | P1 | 2h | A new reader can set up and run everything from the README alone |
| **DOC-25** | Project synopsis corrections annexure | VD | 7 | P1 | 2h | All eight items from audit §8 listed as "designed vs delivered, and why" |

---

## F. Demo & Viva Readiness — 7 tasks

| ID | Task | Owner | Week | Pri | Est | Acceptance criterion |
|---|---|---|---|---|---|---|
| **DEMO-01** | Demo runbook — exact script, roles, device checklist | TB | 7 | P0 | 3h | Anyone on the team can run the demo from the document alone |
| **DEMO-02** | Seed demo data: officer accounts, volunteer accounts, sample alerts | VD | 7 | P0 | 2h | Fresh environment ready in under 5 minutes |
| **DEMO-03** | Rehearsal 1 — full flow, timed | All | 7 | P0 | 3h | Complete run in under 10 minutes; every failure logged and fixed |
| **DEMO-04** | Rehearsal 2 — including the offline mesh scenario | All | 8 | P0 | 3h | Airplane-mode demo works twice consecutively |
| **DEMO-05** | Record the demo video (5–7 min, narrated) | AN | 8 | P0 | 5h | Covers online path, offline mesh, privacy claim, and the kiosk dispatch |
| **DEMO-06** | Offline fallback plan for the viva | SB | 8 | P1 | 2h | Local hotspot configured; pre-recorded video ready; screenshots printed |
| **DEMO-07** | Viva question preparation | All | 8 | P1 | 3h | Written answers to the 20 likely questions in `05-risks-and-demo.md` |

---

## Effort Summary

| Section | Tasks | Hours |
|---|---|---|
| A. Deployment & Configuration | 8 | 17 |
| B. Verification & Measurement | 16 | 59 |
| C. Feature Gaps | 13 | 44 (32 if the Pi is descoped) |
| D. Engineering Quality | 10 | 27 |
| E. Documentation | 26 | 123 |
| F. Demo & Viva | 7 | 21 |
| **Total** | **80** | **≈291 h** (279 h with the Pi descoped) |

Across four members over five weeks that is **roughly 14 hours per person per
week**. Achievable alongside classes, but only with no slippage in Week 4 —
because every P0 in Weeks 5–8 depends on the deployment landing on time.

**If you fall behind, cut in this order:** all P3 → GAP-08 (Pi) → GAP-02
(signing) → GAP-04/11/12 (maps) → QA-02/03/09 → DOC-21 (paper, unless required).
**Never cut:** any P0, VER-05, VER-08, VER-12, or DOC-10.
