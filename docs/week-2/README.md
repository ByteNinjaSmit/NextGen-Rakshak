# Week 2 — System Architecture & Design

**Project:** NextGen Rakshak: Smart Edge-Based Lost Child Recovery System for Mass Gatherings
**Group ID:** 6
**Phase:** Design (following Week 1 research)

> **Action required before submission:** fill in the week date range below and in
> each member file, and confirm the work split matches who actually did what.

**Week of:** `____________ to ____________`

---

## Summary

This week we translated the Week 1 research into a concrete system design. We
produced the high-level architecture defining the three core components — the
Police Kiosk Portal (Next.js), the Volunteer Android Application (Kotlin), and
the Offline Mesh Network (Nearby Connections) — and documented the interaction
flow between them, covering both the online path (Firebase Cloud Messaging) and
the offline path (multi-hop store-and-forward routing). We designed UI wireframes
for both applications, planned the Firebase Firestore schema (alerts, volunteers,
matches, and user roles), finalised the technology stack, and specified the
communication protocols between components.

This foundational design work directly supports **Objective 1** by establishing a
clear blueprint for a system that operates independently of unreliable internet
infrastructure during the critical "golden hour."

---

## Work split

| # | Member | Roll No. | Track | Document |
|---|--------|----------|-------|----------|
| 1 | Bankar Smitraj Dinkar | 09 | System architecture & technology stack | [01-bankar-smitraj-system-architecture.md](01-bankar-smitraj-system-architecture.md) |
| 2 | Bhakare Tanishka Sharad | 11 | Interaction flow & communication protocols | [02-bhakare-tanishka-interaction-flow.md](02-bhakare-tanishka-interaction-flow.md) |
| 3 | Dhadge Vedant Sanjay | 34 | Firestore database schema & security model | [03-dhadge-vedant-database-schema.md](03-dhadge-vedant-database-schema.md) |
| 4 | Narkhede Atharva Anantkumar | 94 | UI/UX wireframes (kiosk + Android) | [04-narkhede-atharva-ui-wireframes.md](04-narkhede-atharva-ui-wireframes.md) |

## Deliverables produced

- [x] High-level system architecture diagram (3-layer: cloud / mesh / edge)
- [x] Component responsibility matrix
- [x] Finalised technology stack with pinned versions
- [x] UI wireframes — Police Kiosk Portal (4 screens)
- [x] UI wireframes — Volunteer Android App (4 screens)
- [x] Firestore schema — `alerts`, `volunteers`, `matches`
- [x] Role model and security-rule strategy
- [x] End-to-end interaction flow (online + offline paths)
- [x] Mesh routing protocol specification (message ID, TTL, duplicate suppression)
- [x] Inter-component communication protocol table

## How this maps to the objectives

| Objective | Addressed by |
|-----------|--------------|
| Obj 1 — hybrid edge-AI system for golden-hour recovery | All four tracks (blueprint established) |
| Obj 3 — FCM + offline Nearby Connections mesh | Track 2 (interaction flow, mesh protocol) |
| Obj 4 — Next.js kiosk portal | Tracks 1, 3, 4 |
| Obj 5 — Kotlin volunteer app | Tracks 1, 4 |
| Obj 7 — privacy-by-design | Track 3 (schema: no biometric upload of bystanders) |

## Next week (Week 3)

Begin implementation of the Police Kiosk Portal per the Week 2 blueprint —
project scaffolding, Firebase integration, authentication, and the alert-creation
form against the schema defined in Track 3.
