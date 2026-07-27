# Work Assignments by Member

Each member continues the track they have owned since Week 2. Continuity matters
here for a practical reason: at the viva you are examined on your own track, and
a member who owned mesh routing in Weeks 2, 3 and 5 can answer a question about
TTL decrement that a member who was handed it in Week 6 cannot.

Shared tasks (weekly reports, the regression pass, rehearsals, report assembly)
are split four ways and shown as fractional hours.

| Member | Roll | Track | Total hours (W4–W8) |
|---|---|---|---|
| Narkhede Atharva Anantkumar | 94 | UI/UX both apps, **recognition accuracy**, kiosk hosting, figures, demo assets | ≈82 h |
| Bankar Smitraj Dinkar | 09 | Model pipeline, build/release, device performance, CI, **integration lead** | ≈78 h (≈70 h if no IEEE paper) |
| Bhakare Tanishka Sharad | 11 | Cloud backend, **server-side embedding**, FCM, offline mesh, deployment | ≈64 h |
| Dhadge Vedant Sanjay | 34 | Auth/authorisation, data lifecycle, privacy, **model parity**, test process | ≈64 h |

Average **≈14 h per person per week**.

These allocations follow the Week 3 pipeline split, in which the model work was
divided four ways: Smitraj produced the weights and conversion scripts, Tanishka
integrated the model server-side, Vedant certified quantisation parity, and
Atharva measured recognition accuracy and derived the 0.55 threshold. Each
member's Week 4–8 measurement tasks continue the stage they already own — so
**VER-05** (accuracy on children's faces) sits with Atharva as a continuation of
the 36-pair study, **VER-02** (on-device inference time) with Smitraj, and
**DEP-02** (server embedding at runtime) with Tanishka. At the viva, the person
asked about a number is the person who produced it.

Vedant's lighter Week 4 exists so he can start the test case document early — it
is 60+ cases and cannot be written in one sitting.

---

## Bankar Smitraj Dinkar (09) — Track 1: Model, Build, Performance, Integration

You are also the integration lead: you own the exit gates, the weekly rhythm, and
the call on what gets descoped.

### Week 4 — 11.5 h
- **QA-07** Commit the 4 untracked Week-3 documents (15 m)
- **GAP-07** Decide Pi node: build or descope — **write the decision down** (1 h)
- **DEP-08** All four members generate model artefacts locally (2 h)
- **QA-01** GitHub Actions CI: gradle tests, assembleDebug, both `tsc`, lint (3 h)
- **QA-06** Branch protection + feature-branch workflow (1 h)
- Confirm with the guide: exact submission date, deliverable list, whether an IEEE paper is required (1 h)
- Decide the child-face dataset source for VER-05 — **start day one, this has an ethics lead time** (2 h)
- Week 4 report, your track (1.25 h)

### Week 5 — 11.25 h
- **VER-02** Per-face inference time on ≥2 physical phones; mean/p50/p95 vs 200 ms (4 h) — yours because Track 1 owns the on-device recognition path
- **QA-04** Extend Android unit tests to ≥25: crop geometry, mesh TTL/relay, alert merge (4 h)
- **DOC-08** Create `measurements.md`; update it daily, not at the end (2 h)
- Week 5 report (1.25 h)

### Week 6 — 19.25 h
- **VER-07** Degradation study: 1 m / 3 m / 5 m, bright vs dim (4 h)
- **VER-09** 50-concurrent-alert load test (3 h)
- **GAP-09** Correct the `minSdk` API 21 → 24 drift (1 h)
- **DEP-07** Release SHA-1 + signed release APK (3 h)
- **QA-08** Release build config, R8, verify the model asset survives shrinking (3 h)
- **QA-03** Android Compose UI tests: Login, Home, match dialog (4 h)
- Week 6 report (1.25 h)
- *(+12 h for **GAP-08** the Pi node, only if Week 4 decided to build it)*

### Week 7 — 27.5 h ← your heaviest week, plan around it
- **DOC-14** Report ch. 5 Implementation (8 h)
- **DOC-16** Report ch. 7 **Results & Analysis** — the chapter your whole track has been building toward (8 h)
- **DOC-21** IEEE paper draft, *only if required* (8 h)
- **VER-16** Regression pass, your share (1.5 h)
- **DEMO-03** Rehearsal 1 (0.75 h)
- Week 7 report (1.25 h)

### Week 8 — 8.25 h
- **DOC-24** Final README / CLAUDE.md update (2 h)
- **DEMO-06** Offline fallback plan: hotspot, video, printed screenshots (2 h)
- **DEMO-04** Rehearsal 2 (0.75 h) · **DEMO-07** Viva prep (0.75 h)
- Report assembly, your share (1.5 h) · Week 8 report (1.25 h)

---

## Bhakare Tanishka Sharad (11) — Track 2: Cloud Backend, FCM, Offline Mesh

The mesh is the project's core contribution over every system in the literature
survey. It is also the least-tested thing in the repository. Week 5 is yours.

### Week 4 — 12.25 h
- **DEP-01** Deploy functions, Firestore rules, Storage rules, indexes (3 h)
- **DEP-02** Verify `tfjs-node` loads; confirm `onAlertCreated` logs `dims: 128` (2 h) ← **if this fails, escalate the same day**
- **DEP-03** Confirm the SavedModel ships in the deploy bundle (1 h)
- **VER-03** End-to-end online path: alert → FCM → phone → match → kiosk row (4 h)
- Arrange ≥3 Android phones for the Week 5 mesh trial (1 h)
- Week 4 report (1.25 h)

### Week 5 — 17.75 h
- **VER-08** **Multi-device mesh trial, 3+ phones, zero internet** — multi-hop delivery, TTL decrement, duplicate suppression, delivery time vs NFR-05 (6 h)
- **VER-04** Geofence: inside 2 km, outside 5 km, unknown location fails open (3 h)
- **GAP-01** Implement `onPayloadTransferUpdate` — log failures, retry once (3 h)
- **GAP-06** Low-res thumbnail over the mesh, with Atharva (2.5 h) — **do this before the mesh trial** so the offline demo shows a photo
- **GAP-13** "Embedding not ready" state on the app (2 h)
- Week 5 report (1.25 h)

### Week 6 — 19.25 h
- **GAP-02** Mesh payload signing — implement HMAC or write the descope (5 h)
- **GAP-10** Document the kiosk-cannot-use-Nearby-Connections constraint (1 h)
- **QA-05** Functions emulator tests: expiry, purge, geofence (4 h)
- **DOC-13** Report ch. 4 **System Design** — architecture, DFDs, ER, sequence diagrams, mesh protocol (8 h)
- Week 6 report (1.25 h)

### Week 7 — 10.5 h
- **DOC-20** Deployment / user manual: officer guide, volunteer guide, admin runbook (4 h)
- **DEMO-01** Demo runbook — exact script, roles, device checklist (3 h)
- **VER-16** Regression pass, your share (1.5 h) · **DEMO-03** Rehearsal 1 (0.75 h)
- Week 7 report (1.25 h)

### Week 8 — 4.25 h
- **DEMO-04** Rehearsal 2, including the offline scenario (0.75 h) · **DEMO-07** Viva prep (0.75 h)
- Report assembly, your share (1.5 h) · Week 8 report (1.25 h)

---

## Dhadge Vedant Sanjay (34) — Track 3: Auth, Authorisation, Data Lifecycle, Test Process

Your Weeks 4 and 5 are deliberately light so you can start the test case document
early. Sixty test cases written in Week 6 alone will be sixty bad test cases.

### Week 4 — 4.25 h (+ start DOC-09 and DOC-10 early)
- **DEP-05** Allow-list two officers; verify the grant path **and** the deny path (2 h)
- **DEP-06** Enable Email/Password + Anonymous providers; exercise both routes on a real phone (1 h)
- Week 4 report (1.25 h)
- *Use the spare time to begin the test plan (DOC-09) and sketch the test case document*

### Week 5 — 18.25 h
- **VER-15** Firestore rules negative-test suite on the emulator: volunteer cannot create an alert; nobody can delete; `allowedOfficers` unreadable (4 h)
- **VER-10** Offline match queue: airplane mode → confirm → reconnect → kiosk row (3 h)
- **VER-11** Alert expiry sweep + photo purge from Storage (2 h)
- **DOC-09** Test plan document (4 h)
- **DOC-11b** Report ch. 2 Literature Survey (4 h)
- Week 5 report (1.25 h)

### Week 6 — 19.25 h
- **VER-12** **Privacy evidence for NFR-08** — network capture or audit proving no bystander biometric leaves the phone (3 h). This is the project's headline claim; evidence it rather than asserting it
- **QA-09** Crashlytics (2 h)
- **DOC-10** **Test case document, ≥60 cases** covering every FR and NFR (8 h)
- **DOC-12** Report ch. 3 Requirement Analysis, with the eight synopsis corrections integrated (5 h)
- Week 6 report (1.25 h)

### Week 7 — 15.5 h
- **DOC-15** Report ch. 6 Testing — strategy, cases, defect log including the 7 Week-3 defects (6 h)
- **DOC-25** Synopsis corrections annexure: designed vs delivered, and why (2 h)
- **QA-10** Third-party licence inventory (2 h)
- **DEMO-02** Seed demo data: officer and volunteer accounts, sample alerts (2 h)
- **VER-16** Regression pass, your share (1.5 h) · **DEMO-03** Rehearsal 1 (0.75 h)
- Week 7 report (1.25 h)

### Week 8 — 6.25 h
- **DOC-22** Plagiarism check on the final report (2 h)
- **DEMO-04** Rehearsal 2 (0.75 h) · **DEMO-07** Viva prep (0.75 h)
- Report assembly, your share (1.5 h) · Week 8 report (1.25 h)

---

## Narkhede Atharva Anantkumar (94) — Track 4: UI/UX, Hosting, Figures, Demo Assets

You own everything the examiner actually looks at: the screens, the figures, the
slides and the video. That is not a lightweight role — it is the presentation
layer of the entire project.

### Week 4 — 11.75 h
- **DEP-04** Host the kiosk on a public HTTPS URL (3 h)
- **VER-01** Run the app on 2 physical phones, all four screens, both sizes (3 h)
- **DOC-01** Fill every blank week date range in `docs/` (30 m)
- **DOC-02** Week 1 report — literature survey, 4-track structure, from synopsis §4 (4 h)
- Week 4 report (1.25 h)

### Week 5 — 18.75 h
- **VER-05** Recognition accuracy on children's faces vs the 90% KPI (6 h) ← **highest-risk task in the project**, and a direct continuation of your Week 3 36-pair study
- **VER-06** ML Kit detection accuracy vs 95% (3 h)
- **GAP-03** Notification tap opens the Scan screen directly (2 h)
- **GAP-06** Low-res thumbnail over the mesh, with Tanishka — the UI side (2.5 h)
- **DOC-11a** Report ch. 1 Introduction — **started early on purpose** (4 h)
- Assist the Wednesday mesh trial (all hands)
- Week 5 report (1.25 h)

### Week 6 — 19.25 h
- **GAP-04** Dashboard map of recent match locations (4 h)
- **GAP-11** Map pin per match on the matches page (3 h)
- **GAP-05** Kiosk offline banner + retry (2 h)
- **VER-13** Battery impact: 30 min scanning vs 30 min idle (3 h)
- **VER-14** Scan throughput vs 200 faces/hour (2 h)
- **QA-02** Web portal tests + one Playwright smoke test (4 h)
- Week 6 report (1.25 h)

### Week 7 — 16.5 h
- **DOC-19** Final figures: architecture, flow, DFD, ER, sequence, mesh topology (6 h)
- **DOC-17** Report ch. 8 Conclusion + Future Scope (4 h)
- **DOC-18** Comparison table vs TrackChild / Khoya-Paya / ReUnite / Garuda / Maha Kumbh / AMBER (3 h)
- **VER-16** Regression pass, your share (1.5 h) · **DEMO-03** Rehearsal 1 (0.75 h)
- Week 7 report (1.25 h)

### Week 8 — 15.25 h ← your heaviest week
- **DOC-23** Presentation slides, 20–25 (6 h)
- **DEMO-05** Record the narrated demo video, 5–7 min (5 h)
- **DEMO-04** Rehearsal 2 (0.75 h) · **DEMO-07** Viva prep (0.75 h)
- Report assembly, your share (1.5 h) · Week 8 report (1.25 h)

---

## Load by Week

| Member | W4 | W5 | W6 | W7 | W8 | Total |
|---|---|---|---|---|---|---|
| Atharva (94) | 11.75 | 18.75 | 19.25 | 16.5 | 15.25 | **81.5** |
| Smitraj (09) | 11.5 | 11.25 | 19.25 | 27.5 | 8.25 | **77.75** |
| Tanishka (11) | 12.25 | 17.75 | 19.25 | 10.5 | 4.25 | **64** |
| Vedant (34) | 4.25 | 18.25 | 19.25 | 15.5 | 6.25 | **63.5** |
| **Team** | **39.75** | **66** | **77** | **70** | **34** | **≈287** |

**Watch Week 7 for Smitraj (27.5 h).** Three report chapters plus the IEEE paper
in one week is the plan's weakest point. Two mitigations, pick one in Week 6:
confirm the IEEE paper is not required (saves 8 h outright), or start DOC-16
Results in Week 6 as soon as the last measurement lands — the data is complete by
then, only the writing is not. His lighter Week 5 (11.25 h) exists partly for
this: bring writing forward into it if the measurements are landing on schedule.

## Stretch items — do only if a week finishes early

| ID | Task | Why it is optional |
|---|---|---|
| GAP-12 | Multi-sighting grouping on the kiosk | P3, no requirement depends on it |
| GAP-08 | Raspberry Pi node | FR-13 is *Low (Optional)*; 12 h that Week 6 does not have |
| QA-03 extras | Wider UI test coverage | Diminishing returns past the three core screens |
