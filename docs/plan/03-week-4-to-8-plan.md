# Week 4 → Week 8 Plan

Each week has a **theme**, a **task list**, a **day-by-day breakdown**, and an
**exit gate**. The exit gate is not optional: if a week's gate is not met, the
following week starts with catching up, not with new work. Say so out loud at the
weekly review — a schedule that hides slippage costs you Week 8.

---

## Week 4 — Deploy and Unblock
**27 Jul – 02 Aug 2026** · 20 tasks · ≈40 h

### Why this week matters

Nothing in this project has ever run on real infrastructure. The Cloud Functions
have never been deployed, the kiosk has never been hosted, and no officer has
ever been authorised — meaning the kiosk is currently unusable by anybody. Every
measurement in Week 5 depends on this week landing. **If one week is allowed to
slip, it must not be this one.**

### Tasks

| ID | Task | Owner | Est |
|---|---|---|---|
| QA-07 | Commit the 4 untracked Week-3 documents | SB | 15m |
| GAP-07 | **Decide: build the Raspberry Pi node or descope it** | SB | 1h |
| DEP-01 | Deploy functions + rules + indexes | TB | 3h |
| DEP-02 | Verify `tfjs-node` loads; embedding logs `dims: 128` | TB | 2h |
| DEP-03 | Confirm the SavedModel ships in the bundle | TB | 1h |
| DEP-04 | Host the kiosk on a public URL | AN | 3h |
| DEP-05 | Allow-list two officers; verify grant **and** deny | VD | 2h |
| DEP-06 | Enable Email/Password + Anonymous providers; test both routes | VD | 1h |
| DEP-08 | All four members generate model artefacts locally | SB | 2h |
| VER-01 | Run the app on 2 physical phones, all four screens | AN | 3h |
| VER-03 | End-to-end online path, alert → kiosk match row | TB | 4h |
| QA-01 | GitHub Actions CI | SB | 3h |
| QA-06 | Branch protection + feature-branch workflow | SB | 1h |
| DOC-01 | Fill the blank week date ranges | AN | 30m |
| DOC-02 | Week 1 report (literature survey) | AN | 4h |
| DOC-03 | Week 4 report — README + 4 tracks | All | 5h |
| — | Confirm whether an IEEE paper is required (affects DOC-21) | SB | 30m |
| — | Confirm the exact submission date and deliverable list with the guide | SB | 30m |
| — | Book / arrange ≥3 Android phones for the Week 5 mesh trial | TB | 1h |
| — | Decide the child-face dataset source for VER-05 (**start now — this has an ethics lead time**) | SB | 2h |

### Day by day

| Day | Focus |
|---|---|
| Mon 27 | SB: commit docs, Pi decision, confirm deliverables with guide. TB: begin DEP-01. VD: DEP-05 + DEP-06. AN: DEP-04 |
| Tue 28 | TB: DEP-02/03 — the make-or-break day; if `tfjs-node` fails, decide the fallback today. AN: DEP-04 finishes, VER-01 begins |
| Wed 29 | TB: VER-03 end-to-end. VD: verify the deny path and both new sign-in routes on a phone. SB: QA-01 CI |
| Thu 30 | SB: DEP-08 + dataset decision. AN: VER-01 on the second phone + DOC-01. TB: fix whatever VER-03 exposed |
| Fri 31 | Buffer for deployment fallout — assume there will be some. SB: QA-06 |
| Sat 01 | AN: DOC-02 Week 1 report. Everyone drafts their Week 4 track |
| Sun 02 | DOC-03 assembled and committed. **Exit gate review.** Phones arranged for Monday |

### Exit gate — all must be true

- [ ] A real alert created on the hosted kiosk gets an embedding computed by the deployed function
- [ ] That alert reaches a physical phone by FCM
- [ ] A match confirmed on that phone appears on the kiosk with GPS
- [ ] An allow-listed officer gets in; a non-allow-listed Google account is rejected
- [ ] All three sign-in routes work on a physical device
- [ ] CI is green on `main`
- [ ] The Pi decision is written down
- [ ] Week 4 documentation committed

> **If `tfjs-node` will not run in the Functions runtime** — the one plausible
> hard failure this week — do not spend more than a day on it. Fall back to
> computing the embedding in the browser at alert-creation time using
> TensorFlow.js, or to a small containerised Cloud Run service. Either is a
> defensible engineering decision; a week lost to a native binding is not.

---

## Week 5 — Field Testing and Measurement
**03 Aug – 09 Aug 2026** · 17 tasks · ≈66 h

### Why this week matters

Seven of the ten NFRs currently have no number behind them, and the offline mesh
— the project's core contribution over ReUnite and every centralised system in
the literature survey — has never been run on more than one device. This is the
heaviest week and it is the week that determines what the results chapter can
honestly claim.

### Tasks

| ID | Task | Owner | Est |
|---|---|---|---|
| **VER-08** | **Multi-device mesh trial, 3+ phones, fully offline** | TB | 6h |
| **VER-05** | **Recognition accuracy on children's faces (NFR-02)** | AN | 6h |
| VER-02 | Per-face inference time on device (NFR-03) | SB | 4h |
| VER-06 | ML Kit detection accuracy (NFR-01) | AN | 3h |
| VER-04 | Geofence: inside, outside, and unknown-location cases | TB | 3h |
| VER-10 | Offline match queue: Room + WorkManager round trip | VD | 3h |
| VER-11 | Alert expiry sweep + photo purge | VD | 2h |
| VER-15 | Firestore rules negative-test suite | VD | 4h |
| GAP-06 | Low-resolution thumbnail over the mesh | TB+AN | 5h |
| GAP-01 | `onPayloadTransferUpdate` — log and retry | TB | 3h |
| GAP-03 | Notification tap opens the Scan screen | AN | 2h |
| GAP-13 | "Embedding not ready" state | TB | 2h |
| QA-04 | Extend Android unit tests to ≥25 | SB | 4h |
| DOC-08 | Start `measurements.md` and keep it current daily | SB | 2h |
| DOC-09 | Test plan document | VD | 4h |
| DOC-11a | Report ch. 1 Introduction — **started early to unload Week 7** | AN | 4h |
| DOC-11b | Report ch. 2 Literature Survey | VD | 4h |
| DOC-04 | Week 5 report | All | 5h |

### Day by day

| Day | Focus |
|---|---|
| Mon 03 | SB: VER-02 inference timing (quick, unblocks the results chapter). TB: GAP-06 thumbnail — needed **before** the mesh trial so the offline demo shows a photo. VD: VER-15 rules tests |
| Tue 04 | TB: GAP-01 + GAP-13. AN: VER-05 dataset assembled and run — continues her Week 3 study. AN: GAP-03 |
| Wed 05 | **Mesh trial day.** All four members, 3+ phones, a large room or open ground, all radios off except Bluetooth/Wi-Fi Direct. Budget the whole afternoon; expect the first attempt to fail |
| Thu 06 | Fix whatever the mesh trial exposed; re-run. TB: VER-04 geofence. VD: VER-10 offline queue |
| Fri 07 | AN: VER-06 detection accuracy. SB: QA-04. VD: VER-11 + DOC-09 test plan |
| Sat 08 | Everyone logs their measurements into `measurements.md`. Draft Week 5 tracks |
| Sun 09 | DOC-04 committed. **Exit gate review.** Decide GAP-02 (signing) build-or-descope for Week 6 |

### Exit gate

- [ ] Mesh proven across ≥3 devices with **zero internet**: multi-hop delivery, TTL decrement, duplicate suppression all observed and logged
- [ ] Mesh delivery time measured against NFR-05 (30 s)
- [ ] Inference time measured on ≥2 physical phones, verdict stated against 200 ms
- [ ] Recognition accuracy measured on children's faces, verdict stated against 90%
- [ ] Detection accuracy measured against 95%
- [ ] Offline match queue proven end to end
- [ ] Rules test suite passing
- [ ] `measurements.md` has a row for every NFR attempted

> **The riskiest task of the whole project is VER-05.** Sourcing children's face
> photographs is an ethics question, not a technical one. Options in order of
> preference: (1) a published, licensed child-face research dataset with a clear
> academic-use term; (2) team members' own childhood photographs, with the family's
> written consent; (3) if neither is available in time, measure on the widest adult
> set you can, and **state plainly in the report that NFR-02 is unproven for the
> target population** — that is an honest limitation and a strong future-work
> section. It is not a reason to quietly report the adult number as if it answered
> the KPI.

---

## Week 6 — Gap Closure and Hardening
**10 Aug – 16 Aug 2026** · 21 tasks · ≈77 h (+12 h if the Pi node is built)

### Why this week matters

This is the last week in which any feature may be added. Everything after it is
testing, writing, and rehearsal. The report chapters start here in parallel,
because starting them in Week 7 has ended more final-year projects than any
technical problem.

### Tasks

| ID | Task | Owner | Est |
|---|---|---|---|
| GAP-02 | Mesh payload signing — or the written descope | TB | 5h |
| GAP-04 | Dashboard map of recent matches | AN | 4h |
| GAP-11 | Map pin per match | AN | 3h |
| GAP-05 | Kiosk offline banner | AN | 2h |
| GAP-09 | Correct the `minSdk` drift | SB | 1h |
| GAP-10 | Document the kiosk-cannot-mesh constraint | TB | 1h |
| GAP-08 | Pi node *(only if Week 4 decided to build it)* | SB | 12h |
| VER-07 | Distance / blur / low-light degradation study | SB | 4h |
| VER-09 | 50-concurrent-alert load test | SB | 3h |
| VER-12 | **Privacy evidence for NFR-08** | VD | 3h |
| VER-13 | Battery impact | AN | 3h |
| VER-14 | Scan throughput | AN | 2h |
| DEP-07 | Release SHA-1 + signed release APK | SB | 3h |
| QA-08 | Release build config, R8, shrink | SB | 3h |
| QA-02 | Web portal tests | AN | 4h |
| QA-03 | Compose UI tests | SB | 4h |
| QA-05 | Functions emulator tests | TB | 4h |
| QA-09 | Crashlytics | VD | 2h |
| DOC-10 | **Test case document — ≥60 cases** | VD | 8h |
| DOC-12 | Report ch. 3 Requirement Analysis | VD | 5h |
| DOC-13 | Report ch. 4 System Design | TB | 8h |
| DOC-05 | Week 6 report | All | 5h |

### Day by day

| Day | Focus |
|---|---|
| Mon 10 | Feature work begins: TB GAP-02, AN GAP-04, SB VER-09. VD starts DOC-10 |
| Tue 11 | AN GAP-11 + GAP-05. SB VER-07. TB QA-05 |
| Wed 12 | SB DEP-07 + QA-08 release build. VD VER-12 privacy evidence. AN VER-13/14 |
| Thu 13 | **Feature freeze at end of day.** Anything unfinished is descoped and written up as future work — no exceptions |
| Fri 14 | Report chapters only: TB ch.4, VD ch.3. SB QA-03 |
| Sat 15 | AN QA-02 web tests. VD DOC-10 test cases. SB GAP-09, docs cleanup |
| Sun 16 | DOC-05 committed. **Exit gate review** |

### Exit gate

- [ ] **Feature freeze declared** — no new features after this week
- [ ] Signed release APK exists and runs on a clean phone
- [ ] Every NFR has either a measured number or a written, reasoned limitation
- [ ] Report chapters 1, 2, 3 and 4 drafted
- [ ] Test case document complete with ≥60 cases (results may still be pending)

---

## Week 7 — Full-System Test and Report
**17 Aug – 23 Aug 2026** · 15 tasks · ≈63 h

### Why this week matters

Bug-fix only. This is where the whole system is exercised as one thing against
the test case document, and where the report becomes a document rather than a
folder of chapters.

### Tasks

| ID | Task | Owner | Est |
|---|---|---|---|
| VER-16 | **Full-system regression pass against every test case** | All | 6h |
| DOC-14 | Report ch. 5 Implementation | SB | 8h |
| DOC-15 | Report ch. 6 Testing | VD | 6h |
| DOC-16 | **Report ch. 7 Results & Analysis** | SB | 8h |
| DOC-17 | Report ch. 8 Conclusion + Future Scope | AN | 4h |
| DOC-18 | Comparison table vs existing systems | AN | 3h |
| DOC-19 | Final figures: architecture, flow, DFD, ER, sequence, mesh topology | AN | 6h |
| DOC-20 | Deployment / user manual | TB | 4h |
| DOC-21 | IEEE paper draft *(if required)* | SB | 8h |
| DOC-25 | Synopsis corrections annexure | VD | 2h |
| QA-10 | Licence inventory | VD | 2h |
| DEMO-01 | Demo runbook | TB | 3h |
| DEMO-02 | Seed demo data | VD | 2h |
| DEMO-03 | **Rehearsal 1, timed** | All | 3h |
| DOC-06 | Week 7 report | All | 5h |

### Day by day

| Day | Focus |
|---|---|
| Mon 17 | **Regression day.** All four members work through the test case document against the release build. Log every failure with a severity |
| Tue 18 | Fix P0/P1 defects from Monday. Re-test. Nothing else |
| Wed 19 | SB DOC-16 results chapter — the most important chapter in the report. TB DEMO-01 runbook |
| Thu 20 | SB DOC-14 implementation. VD DOC-15 testing + DOC-25. AN DOC-19 figures |
| Fri 21 | **Rehearsal 1**, timed, everyone present. Log what breaks |
| Sat 22 | Fix rehearsal fallout. TB DOC-20 manual. AN DOC-17/18. SB DOC-21 paper if required |
| Sun 23 | DOC-06 committed. **Exit gate review** |

### Exit gate

- [ ] Every test case executed once with a recorded result
- [ ] Zero known P0 defects
- [ ] Report chapters 1–8 all drafted
- [ ] All final figures produced
- [ ] Demo rehearsed end to end at least once
- [ ] **Code freeze declared** — after this, only defect fixes with a written justification

---

## Week 8 — Final Report, Demo, Viva
**24 Aug – 30 Aug 2026** · 9 tasks · ≈34 h

### Why this week matters

No engineering. Assemble, polish, rehearse, submit. Keep Thursday and Friday
empty on purpose — something always needs them.

### Tasks

| ID | Task | Owner | Est |
|---|---|---|---|
| — | Assemble the full report: cover, certificate, acknowledgement, abstract, ToC, chapters, references, appendices | All | 6h |
| DOC-22 | Plagiarism check | VD | 2h |
| DOC-23 | Presentation slides (20–25) | AN | 6h |
| DOC-24 | Final README / CLAUDE.md update | SB | 2h |
| DEMO-04 | Rehearsal 2 including the offline mesh scenario | All | 3h |
| DEMO-05 | Record the narrated demo video | AN | 5h |
| DEMO-06 | Offline fallback plan for the viva | SB | 2h |
| DEMO-07 | Viva question preparation | All | 3h |
| DOC-07 | Week 8 report | All | 5h |

### Day by day

| Day | Focus |
|---|---|
| Mon 24 | Report assembly begins. AN starts slides. VD runs the plagiarism check |
| Tue 25 | AN records the demo video. SB DOC-24 + DEMO-06 |
| Wed 26 | **Rehearsal 2**, including the airplane-mode mesh scenario, twice |
| Thu 27 | Report finalised, proofread by someone other than its author. Slides finished |
| Fri 28 | **Buffer — keep it empty.** Printing, binding, upload, whatever surfaces |
| Sat 29 | DEMO-07 viva prep, mock questions across the whole team |
| Sun 30 | **Submit.** DOC-07 committed. Final git tag `v1.0` |

### Exit gate

- [ ] Final report submitted in the required format
- [ ] Slides done and rehearsed
- [ ] Demo video recorded
- [ ] Repository tagged, README accurate, `git status` clean
- [ ] Every team member can answer for any part of the system, not only their own

---

## Weekly Rhythm

Hold these three meetings every week. They cost 2 hours and they are the reason
the plan survives contact with reality.

| When | Meeting | Duration | Output |
|---|---|---|---|
| Monday, start of day | **Planning** — confirm this week's tasks and owners | 30 min | Anyone blocked says so now |
| Wednesday evening | **Mid-week check** — is the exit gate still reachable? | 30 min | Re-plan or descope, in writing |
| Sunday | **Review + report** — walk the exit gate checklist, write the week's document | 60 min | Week report committed |

**Rule:** a task is not done because someone says it is done. It is done when its
acceptance criterion in `02-backlog.md` has been observed by a second person.
This is the same rule that caught the timestamp bug in Week 3 — the offline mesh
that "worked" and was carrying nothing.
