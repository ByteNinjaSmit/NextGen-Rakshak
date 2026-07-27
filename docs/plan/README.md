# Project Plan — Week 4 to Week 8 (Closure)

**Project:** NextGen Rakshak: Smart Edge-Based Lost Child Recovery System for Mass Gatherings
**Group ID:** 6
**Plan written:** 27 July 2026 (start of Week 4)
**Target close:** end of Week 8 — **Sunday 30 August 2026**

This folder is the single source of truth for what is finished, what is left, who
owns it, and when it must be done. It was produced by auditing every document in
`docs/`, every source file in all four components, the Firebase configuration,
and the project synopsis — not by re-reading the weekly reports alone.

## Week calendar assumed by this plan

| Week | Dates (Mon–Sun) | Theme |
|---|---|---|
| Week 4 | 27 Jul – 02 Aug 2026 | **Deploy and unblock** — get everything running on real infrastructure |
| Week 5 | 03 Aug – 09 Aug 2026 | **Field testing and measurement** — prove the NFRs with numbers |
| Week 6 | 10 Aug – 16 Aug 2026 | **Gap closure and hardening** — last features in, then feature freeze |
| Week 7 | 17 Aug – 23 Aug 2026 | **Full-system test and report writing** — bug-fix only |
| Week 8 | 24 Aug – 30 Aug 2026 | **Final report, demo, viva** — code freeze |

If your college's week boundaries differ, change them here first; everything else
references the week number, not the date.

## Documents

| File | What it is | Read it when |
|---|---|---|
| [01-status-audit.md](01-status-audit.md) | Complete inventory: every component, what is built, what is verified, what is missing | You need to know where the project actually stands |
| [02-backlog.md](02-backlog.md) | Master task list — 80 tasks with ID, owner, week, priority, effort, acceptance criteria | You are assigning or picking up work |
| [03-week-4-to-8-plan.md](03-week-4-to-8-plan.md) | Week-by-week schedule with daily breakdown and exit gates | You are planning the week |
| [04-assignments.md](04-assignments.md) | Per-member workload, week by week, with hour totals | You are a team member asking "what is mine?" |
| [05-risks-and-demo.md](05-risks-and-demo.md) | Risk register, mitigations, demo runbook, viva question prep | You are worried about what can go wrong |
| [measurements.md](measurements.md) | Running log of every measured number, one row per NFR | You measured something — write it down the same day |

## The one-paragraph summary

The **software is substantially built**. All three online components — police
kiosk, Cloud Functions, volunteer Android app — are feature-complete against the
synopsis MVP with the exception of the optional Raspberry Pi node, and the face
recognition pipeline is measured and working. What the project does **not** have
is deployment and evidence: the Cloud Functions have never been deployed, the
kiosk has never been hosted, no officer has ever been authorised, the mesh has
never been run across more than one device, and seven of the ten non-functional
requirements have no measurement behind them. **The gap between now and
submission is mostly verification and documentation, not coding.** That is good
news for the schedule and bad news for anyone who assumed testing would be quick.

## Status at a glance

| Area | Built | Verified on real infrastructure |
|---|---|---|
| Police kiosk (Next.js) | ✅ complete | ❌ never deployed/hosted |
| Cloud Functions | ✅ complete | ❌ never deployed |
| Volunteer Android app | ✅ complete | 🟡 one emulator + Google sign-in only |
| Face recognition model | ✅ complete + measured | 🟡 adults only, no device timing |
| Offline mesh | ✅ complete | ❌ never run on 2+ devices |
| Firestore security rules | ✅ complete | ❌ no rules test suite, no officer allow-listed |
| Raspberry Pi node | ❌ placeholder README only | — |
| Weekly documentation | ✅ Weeks 2–3 | Weeks 4–8 outstanding |
| Final report / paper / slides | ❌ not started | — |
