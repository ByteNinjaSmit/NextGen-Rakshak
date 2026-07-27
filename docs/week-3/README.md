# Week 3 — Implementation & Model Validation

**Project:** NextGen Rakshak: Smart Edge-Based Lost Child Recovery System for Mass Gatherings
**Group ID:** 6
**Phase:** Implementation (following the Week 2 design)

> **Before submission:** fill in the week date range below.

**Week of:** `____________ to ____________`

---

## Summary

Week 2 produced the blueprint; this week we built against it and — critically —
put the face-recognition claim to the test with real measurements instead of
quoted figures.

The headline outcome is that the recognition pipeline now works end to end. We
sourced pretrained MobileFaceNet weights, built a reproducible conversion
pipeline that produces the device and server models from a *single* source, and
measured the system's actual accuracy on real photographs — which led us to
revise the match threshold on evidence rather than keep the figure we had
inherited from the literature.

Alongside this we completed the volunteer Android application against the Week 2
specification, finished the Cloud Functions, and reworked authentication across
both applications so that signing in and being *authorised* are no longer the
same thing — the app gained Google and email/password sign-in, and the kiosk
gained an authorisation check it had never had.

Seven defects were found and fixed. Three came from reading the recognition path,
one of which had silently disabled the entire offline mesh, and one from
reviewing the kiosk, where any Google account could have filed or resolved
missing-child alerts. The remaining three only appeared once the app was
installed and run — including a crash on launch — which is the clearest argument
this week for exercising the build rather than trusting that it compiles.

The app was then brought to a clean launch on a device: no crash, no Android 15
compatibility warning, and a login screen that renders correctly. Google sign-in
was exercised end to end on that device, reaching the home screen and reading
from Firestore under the new rules — the first proof that authentication, the
rules and the crash fix hold together at runtime.

This directly advances **Objective 2** (on-device detection and recognition in
real time), **Objective 7** (privacy and trust by design) and **Objective 8**
(validating the system's effectiveness rather than asserting it).

---

## Work split

| # | Member | Roll No. | Track | Document |
|---|--------|----------|-------|----------|
| 1 | Bankar Smitraj Dinkar | 09 | Model weights, conversion pipeline, on-device recognition, build infrastructure | [01-bankar-smitraj-model-and-build.md](01-bankar-smitraj-model-and-build.md) |
| 2 | Bhakare Tanishka Sharad | 11 | Cloud backend, server-side embedding, FCM geofence, offline mesh | [02-bhakare-tanishka-cloud-backend-and-mesh.md](02-bhakare-tanishka-cloud-backend-and-mesh.md) |
| 3 | Dhadge Vedant Sanjay | 34 | Authentication, authorisation, data lifecycle, model parity verification | [03-dhadge-vedant-auth-and-data-lifecycle.md](03-dhadge-vedant-auth-and-data-lifecycle.md) |
| 4 | Narkhede Atharva Anantkumar | 94 | Volunteer app UI completion, recognition accuracy measurement | [04-narkhede-atharva-ui-completion.md](04-narkhede-atharva-ui-completion.md) |

The face-recognition pipeline was the week's blocking dependency and was large
enough to be split across all four tracks rather than owned by one:

| Pipeline stage | Owner | Output |
|---|---|---|
| Weight sourcing, freeze + convert scripts, quantisation | Bankar Smitraj (09) | 1.5 MB `.tflite` + SavedModel from one source |
| Server-side SavedModel integration in the Cloud Function | Bhakare Tanishka (11) | `onAlertCreated` computes the embedding on the same graph |
| Parity verification of the quantized model | Dhadge Vedant (34) | cosine **0.99967** against the source graph |
| 36-pair accuracy study and the threshold measurement | Narkhede Atharva (94) | separation gap **0.3591**; threshold 0.75 → **0.55** |

Production and verification sit with different members on purpose: the person who
converts a model is the worst-placed person to certify that the conversion was
lossless.

---

## 1. Face recognition model — the blocking dependency

Until this week the system could not recognise anyone: the code was complete but
no model weights existed, so no embedding had ever been computed.

*Sourcing and conversion: Bankar Smitraj (09). Parity verification: Dhadge Vedant
(34). Server integration: Bhakare Tanishka (11).*

### 1.1 Source

| Property | Value |
|---|---|
| Source | [sirius-ai/MobileFaceNet_TF](https://github.com/sirius-ai/MobileFaceNet_TF) |
| File | `arch/pretrained_model/MobileFaceNet_9925_9680.pb` (~5.9 MB) |
| Licence | Apache-2.0 |
| Reported accuracy | 99.25% on the LFW benchmark (encoded in the filename) |
| Input | `img_inputs` `[-1, 112, 112, 3]` |
| Output | `embeddings` `[-1, 128]`, **already L2-normalized by the graph** |

The graph's own output normalisation is convenient: because every embedding is a
unit vector, cosine similarity reduces to a dot product and needs no extra
normalisation step on the device.

### 1.2 Conversion pipeline

Weights are **not committed** to the repository (size and licensing). They are
regenerated by two scripts, so any team member can reproduce them:

```mermaid
flowchart LR
    A["MobileFaceNet_9925_9680.pb<br/>TF1 frozen graph"] --> B["freeze_to_savedmodel.py"]
    B --> C["TF2 SavedModel<br/>batch-1 serving_default"]
    C --> D["convert_models.py"]
    D --> E["mobilefacenet.tflite<br/>1.5 MB quantized<br/>→ Android assets"]
    D --> F["savedmodel/<br/>→ functions/model/"]
    C --> G["verify_parity.py"]
    E --> G
    G --> H["cosine 0.99967 ✅"]

    classDef ok fill:#e8f5e9,stroke:#2e7d32
    classDef art fill:#e3f2fd,stroke:#1565c0
    class E,F art
    class H ok
```

```bash
pip install tensorflow
curl -L -o mobilefacenet.pb \
  https://raw.githubusercontent.com/sirius-ai/MobileFaceNet_TF/master/arch/pretrained_model/MobileFaceNet_9925_9680.pb
python scripts/freeze_to_savedmodel.py --pb mobilefacenet.pb --out ./mobilefacenet_savedmodel
python scripts/convert_models.py       --saved-model ./mobilefacenet_savedmodel
python scripts/verify_parity.py        --saved-model ./mobilefacenet_savedmodel
```

Dynamic-range quantisation shrinks the model from 5.9 MB to **1.5 MB** for the
phone. `verify_parity.py` then runs one input through both the quantized
`.tflite` and the source SavedModel and asserts they agree — measured
**cosine 0.99967**, confirming quantisation did not damage the embedding. That
check was written and run by Track 3, independently of the track that produced
the artefacts.

### 1.3 Architecture change — server loads the SavedModel directly

*Owner: Bhakare Tanishka (11) — Track 2.*

The Week 2 design had the Cloud Function load a TensorFlow.js `GraphModel`
converted from the same weights. We changed this: `tfjs-node` can load a
TensorFlow SavedModel directly, so the server now runs **the identical graph**
that was quantized for the device, eliminating a second conversion that could
drift from the first.

This also removed a hard blocker: `tensorflowjs_converter` cannot run on
Windows at all, because it imports `tensorflow_decision_forests`, which has no
Windows build. The SavedModel route makes the project set up cleanly on the
team's machines.

---

## 2. Measured accuracy — validating the design assumptions

*Owner: Narkhede Atharva (94) — Track 4. Threshold adopted in code by Track 1.*

Rather than restate the synopsis figures, we measured them. Nine photographs of
four people were run through the **actual quantized `.tflite`**, using the app's
own crop geometry — 36 pairs in total.

| Group | Pairs | Cosine range |
|---|---|---|
| Same person | 15 | **0.7142 – 0.9899** |
| Different people | 21 | **0.0864 – 0.3551** |
| **Separation gap** | | **0.3591** |

```mermaid
flowchart LR
    subgraph S[" "]
    direction TB
    A["different people<br/>0.0864 – 0.3551"] -.->|"gap 0.3591"| B["same person<br/>0.7142 – 0.9899"]
    end
    T["threshold 0.55<br/>sits in the gap"] -.-> B

    classDef d fill:#ffebee,stroke:#c62828
    classDef s fill:#e8f5e9,stroke:#2e7d32
    classDef t fill:#fff3e0,stroke:#ef6c00
    class A d
    class B s
    class T t
```

The model separates identities cleanly — no different-person pair came close to
any same-person pair.

### Revising the match threshold: 0.75 → 0.55

The measurement changed a design parameter. The two groups are separated by an
empty band running from **0.3551 to 0.7142**; any threshold inside that band
classifies all 36 pairs correctly. The synopsis figure of 0.75 sits *above* the
band — inside the same-person range — and therefore rejects genuine matches:

| Threshold | False matches | Missed matches |
|---|---|---|
| 0.75 (as designed in Week 2) | 0 / 21 | **5 / 15** |
| **0.55 (adopted)** | **0 / 21** | **0 / 15** |

We adopted **0.55**, which sits near the middle of the empty band with roughly
0.19 of headroom on each side, so it tolerates harder pairs than our sample
contains.

The asymmetry of the two error types justifies favouring the lower value. A
missed child is the precise failure this system exists to prevent. A false
candidate costs only the moment a volunteer takes to tap "Not a match" — and
because the design already requires every match to be human-confirmed before it
reaches police, false positives are cheap by construction while false negatives
are not.

This supersedes the 0.75 figure in the synopsis and in the Week 2 design
documents, which was taken from the literature before we had a model to measure.
The Week 2 documents are left unchanged as the record of the design as it stood
then; `Constants.SIMILARITY_THRESHOLD` and `scripts/README.md` carry the current
value and the evidence for it.

---

## 3. Volunteer Android application — completed to specification

The app was audited screen by screen against synopsis §6.2 and the gaps closed.

| Requirement | Work done |
|---|---|
| §6.2.3 / FR-04 — discard non-frontal faces | The head-Euler check was specified but **never applied**: the detector captured only roll and the matcher ignored it entirely. Now captures **yaw** (the dominant frontality signal) and filters before embedding — limits 30° yaw / 40° roll |
| §6.2.3 — scan overlay | Camera now overlays "Scanning for \<names\>…" |
| §6.2.4 / FR-07 — side-by-side comparison | Match dialog previously showed only the parent's photo. Now shows it beside the face captured live, each captioned, so the volunteer judges the images rather than trusting the score |
| §6.2.4 — vibrate on match | Added, as a double pulse distinct from a normal notification |
| §6.2.2 — time elapsed | Alert rows now show "12 min ago", refreshed every 30 s — the golden-hour clock was not previously on screen |
| §6.2.1 — permission rationale | Permissions were requested cold; a dialog now explains each one and states that matching is on-device and no bystander's face is uploaded |

---

## 4. Cloud backend

| Requirement | Work done |
|---|---|
| FR-03 — geofenced alerts | Push is now filtered by haversine distance to a 2 km radius. Fails **open**: a volunteer with no known location is still notified, because a missed nearby helper costs a child while a spurious notification costs one buzz |
| FR-12 — alert expiry | Reconciled to 8 hours across the Cloud Function, the mesh, and the synopsis (the server had 2 h) |
| NFR-08 — privacy | Resolving an alert cleared the embedding but left the child's photo in Cloud Storage indefinitely. An `onAlertResolved` trigger now deletes the photo and clears the embedding on the `active → resolved` transition, covering both the officer's manual resolve and the scheduled sweep |
| FR-14 — mesh routing | Added the TTL/hop-count the design specified: packets now carry a hop count that is decremented at each relay and dropped at zero, plus an expiry check |

---

## 5. Authentication and authorisation

Both applications signed people in, but neither checked whether the person was
allowed to do what they were about to do. This week separated the two concepts.

### 5.1 Volunteer app — three sign-in routes

The app previously asked for a phone number in a text box and signed the device
in **anonymously**. A sighting sent to police therefore carried no identity: the
officer receiving it had no way to know who reported it, and nothing prevented
one person registering repeatedly. That is incompatible with the synopsis's
"trusted, pre-registered volunteer" model, on which the system's credibility
rests.

| Route | Identity | Implementation |
|---|---|---|
| **Continue with Google** | Verified account | Credential Manager — the current API; the `GoogleSignIn` client it replaces is deprecated. The Google ID token is exchanged for a Firebase session |
| **Email + password** | Verified account | Firebase Email/Password, including account creation for a volunteer issued no credentials |
| **Continue as guest** | Anonymous | Retained for demonstrations, labelled on screen as creating an account that cannot be traced back to the reporter |

For both verified routes the volunteer's name and email are stored with their
role and written to `volunteers/{uid}`, so every match is attributable to a real
account. The role is chosen before signing in, because it is what the officer
sees beside a match and no identity provider can supply it.

Firebase reports email failures as opaque codes such as
`ERROR_INVALID_CREDENTIAL`. These are translated into text a volunteer can act
on — wrong password, no such account, address already registered, password too
short — rather than shown raw.

**Sign-out existed but could not work.** It called `signOut()`, which clears the
stored profile asynchronously, and *immediately* navigated to the login screen.
The login screen still saw the previous volunteer and redirected straight back to
home, leaving the user on the home screen while signed out. Navigation now reacts
to the profile actually becoming null, so the screen changes only once sign-out
has taken effect.

### 5.2 Police kiosk — authentication was not authorisation

Reviewing the kiosk surfaced the most serious defect of the week.

The portal authenticated officers with Google but never checked **which** Google
account had signed in — so any account on the internet could reach the full
kiosk. Compounding it, `firestore.rules` permitted `alerts` create and update to
anyone satisfying `signedIn()`, and that includes every volunteer device, because
the app signs in anonymously. The practical consequence: a volunteer's phone, or
any stranger with a Google account, could file fabricated missing-child alerts or
mark genuine ones resolved.

Synopsis §6.1.1 had already specified the remedy — *"attaches a `role: police`
custom claim; Firestore security rules use this claim to gate write access to the
alerts collection"* — it had simply never been implemented. It now is:

```mermaid
flowchart TB
    A[Officer signs in with Google] --> B[claimOfficerRole callable]
    B --> C{email in<br/>allowedOfficers?}
    C -->|no| D[Signed straight back out<br/>with an explanation]
    C -->|yes| E[Grant custom claim<br/>role = police]
    E --> F[Client refreshes ID token]
    F --> G[Firestore rules allow<br/>alert writes]

    classDef no fill:#ffebee,stroke:#c62828
    classDef yes fill:#e8f5e9,stroke:#2e7d32
    class D no
    class E,F,G yes
```

| Principal | Signs in via | Permitted writes |
|---|---|---|
| Officer (kiosk) | Google **+** `allowedOfficers` entry → `police` claim | Create/resolve alerts; update match status |
| Volunteer (app) | Google (anonymous = demo fallback) | Own `volunteers/{uid}` doc; create matches |
| Any signed-in user | — | Nothing further; no client may delete anything |

Two design points worth noting:

- **The allow-list is invisible to clients.** `allowedOfficers` denies all client
  read and write, so it is reachable only by the Admin SDK inside the Cloud
  Function. A signed-in user can neither enumerate authorised officers nor add
  themselves.
- **The claim is re-checked on every auth state change,** not just at sign-in. A
  session restored on page reload never passes through the sign-in path, so
  checking only there would have left the hole open.

Firestore rules are the enforcement boundary; the kiosk's UI check is a
convenience on top of it.

## 6. Defects found

### 6.1–6.3 Found by reading the recognition path

Three bugs were found by reading the recognition path end to end. The first was
serious.

**6.1 The offline mesh was silently carrying nothing.**
Alert timestamps were read from Firestore as epoch **seconds** while every
consumer treated them as **milliseconds**. The resulting age of every alert
computed to roughly 55 years, so the expiry check rejected *everything*:
broadcasting bailed out immediately and every received packet was dropped. The
offline path — a core contribution of the project — did nothing, with no error to
reveal it. Fixed, unit documented on the model, and pinned by a regression test.

**6.2 Device and server framed faces differently.**
Both sides squashed a raw detector box into the square 112×112 input, but the
device detects with ML Kit and the server with BlazeFace, whose boxes frame faces
differently — so the same child produced differently framed inputs, and
non-square boxes were distorted by an aspect-dependent amount. Against a fixed
threshold this systematically depressed true matches. Both sides now crop a
square centred on the box with a shared 0.2 margin.

**6.3 The model was reloaded and leaked on every scan.**
A new TFLite interpreter was constructed each time the scan screen opened, so the
model was re-read and the previous interpreter abandoned (nothing closes one).
Now a singleton, with 4 inference threads for the <200 ms budget and serialised
access, since TFLite interpreters are not thread-safe.

### 6.4–6.6 Found by running the app on a device

Installing the build on an emulator surfaced three more that no amount of reading
had caught. This is the argument for running the thing.

**6.4 A Firestore error killed the whole app.**
The app crashed on launch:

```
FATAL EXCEPTION: DefaultDispatcher-worker-3
com.google.firebase.firestore.FirebaseFirestoreException:
    PERMISSION_DENIED: Missing or insufficient permissions.
```

`FirestoreAlertSource` called `close(error)` when its snapshot listener failed.
That rethrows the exception in every collector, and because the alert flow is
collected in a ViewModel coroutine, it took the process down. The severity is not
the permission error itself but the response to it: at a festival, a dropped
connection or a rules change would kill the app **mid-search**. The listener now
logs and emits an empty list, keeping the flow alive — and the mesh can still
deliver alerts when Firestore cannot.

**6.5 A stored profile could outlive its Firebase session.**
The cause of that permission error was itself a defect. The volunteer profile is
persisted locally but the Firebase session is not, so once the session was gone
the app went straight to the home screen and every read was rejected. The login
view model now discards a stored profile whose session no longer exists, sending
the volunteer back to sign in.

**6.6 The heading was drawn under the camera cutout.**
The activity draws edge to edge, but the login screen applied no window insets,
so "Rakshak Volunteer" was sliced by the status bar and the camera punch-hole.
Fixed with `safeDrawing` insets.

---

## 7. Build infrastructure

The Android module had `gradle-wrapper.properties` but no `gradlew`,
`gradlew.bat`, or `gradle-wrapper.jar`, so it could only be built from Android
Studio — no command-line build, no CI, and no way to run the unit tests. The
wrapper (Gradle 8.14, from the official repository at tag `v8.14.0`) is now
committed:

```bash
cd nextgen-rakshak-mobile
./gradlew :app:testDebugUnitTest   # compile + run unit tests
./gradlew :app:assembleDebug       # build the APK
```

### 7.1 16 KB page-size compliance

Android 15 warned on every launch that the app was not 16 KB compatible, naming
the native libraries whose ELF `LOAD` segments were aligned to 4 KB. Devices with
16 KB memory pages otherwise fall back to a compatibility mode.

Alignment is a property of each dependency's prebuilt `.so`, so the fix was to
move to versions that ship aligned binaries: **datastore 1.1.7**, **CameraX
1.4.2**, and — for the last offender — replacing `org.tensorflow:tensorflow-lite`
with **`com.google.ai.edge.litert:litert:2.1.6`**, the current name for the same
runtime. It exposes the identical `org.tensorflow.lite.Interpreter` API, so no
application code changed.

LiteRT is built against Kotlin 2.3 metadata, which a 1.9 compiler cannot read, so
it pulled a toolchain upgrade with it:

| Component | Was | Now | Why |
|---|---|---|---|
| Kotlin | 1.9.24 | **2.3.21** | LiteRT's metadata version |
| Compose compiler | `composeOptions` setting | **`kotlin.plugin.compose`** | Separate plugin from Kotlin 2.0 |
| KSP | 1.9.24-1.0.20 | **2.3.10** | Must track Kotlin |
| AGP | 8.5.0 | **8.13.2** | KSP 2.3 needs `addKspConfigurations` |
| compileSdk | 34 | **35** | Required by AGP 8.13 dependencies |
| Room | 2.6.1 | **2.8.4** | KSP2 failed on the old compiler |

`tensorflow-lite-support` was dropped rather than upgraded: nothing used it, and
it pulled in the old `tensorflow-lite-api`, whose `org.tensorflow.lite.*` classes
collide with LiteRT's.

Compliance was verified by reading the `PT_LOAD` `p_align` field directly out of
the built APK rather than trusting the absence of a warning:

| ABI | Result |
|---|---|
| arm64-v8a | all 8 libraries **16384** ✅ |
| x86_64 | all 9 libraries **16384** ✅ |
| armeabi-v7a, x86 | ML Kit's face detector remains 4096 — harmless, as the 16 KB requirement applies only to 64-bit ABIs |

---

## 8. Verification evidence

Each claim below was produced by running something, not by inspection.

**Build and tests**

| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | **BUILD SUCCESSFUL** — 13 tests, 0 failures |
| `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** |
| `functions` `tsc --noEmit` | clean (exit 0) |
| `webportal` `tsc --noEmit` | clean (exit 0) |

**Model**

| Check | Result |
|---|---|
| SavedModel output shape | `(1, 128)`, L2 norm **1.000000** — unit vectors, so cosine is a dot product |
| `verify_parity.py` (quantized `.tflite` vs source SavedModel) | cosine **0.99967**, threshold 0.99 |
| Quantisation | 5.9 MB → **1.5 MB**, embedding preserved |

**Recognition accuracy** — 9 photographs of 4 people, 36 pairs, through the
actual `.tflite` using the app's own crop geometry:

| Metric | Result |
|---|---|
| Same-person cosine | 0.7142 – 0.9899 |
| Different-person cosine | 0.0864 – 0.3551 |
| Separation gap | **0.3591** |
| At threshold 0.55 | **0 false matches / 21**, **0 missed / 15** |
| At threshold 0.75 (previous) | 0 false matches / 21, **5 missed / 15** |

**On device (emulator)**

| Check | Result |
|---|---|
| Launch and remain running | process alive after launch |
| Fatal exceptions | **0** (was a crash on launch before §6.4) |
| `PageSizeMismatchDialog` | **0 occurrences** (was shown on every launch) |
| APK native libraries | every 64-bit library **16 KB aligned**, read from ELF `p_align` |
| Login screen layout | heading clear of the status bar and camera cutout |
| Permission rationale | shown before the system prompt on first launch |

### 8.1 Google sign-in, end to end on a device

With the OAuth client configured in Firebase, the full volunteer sign-in path was
exercised on a device for the first time:

| Step | Result |
|---|---|
| Tap "Continue with Google" | Credential Manager shows the system account picker |
| Choose an account | "Signing you in" — Google ID token returned |
| Token exchanged for a Firebase session | succeeded |
| App navigates to Home | **"Rakshak — Active Alerts"** renders, with "Sign out" in the app bar |
| Firestore read under the new rules | succeeded — "No active alerts right now", no `PERMISSION_DENIED`, no crash |

![Google account picker](screenshots/signin-google-account-picker.png)
![Home screen after sign-in](screenshots/home-after-signin.png)

This is the first evidence that authentication, the Firestore rules, and the
crash fix in §6.4 all hold together at runtime: before the fix this same path
terminated the process.

### 8.2 16 KB page-size warning, before and after

| Before | After |
|---|---|
| ![16 KB warning shown on launch](screenshots/before-16kb-warning.png) | ![no warning after the fix](screenshots/after-16kb-fixed.png) |

The dialog named `libtensorflowlite_jni.so` as unaligned. After moving to LiteRT
it no longer appears, and the permission rationale is the first thing shown.

Not yet evidenced: server-side inference and both sign-in flows, all three
blocked on deployment or Firebase configuration rather than on code — see §9.

---

## 9. Known limitations

Stated plainly, because they bound what can currently be claimed:

1. **Server-side inference is not yet verified at runtime.** The `tfjs-node`
   native binding will not load on the development machine (local Node 22 vs the
   Node 20 that Cloud Functions pins). The code typechecks; confirm on first
   deploy that `onAlertCreated` logs `dims: 128`.
2. **Accuracy was measured on adult faces** from a public sample, not on children
   at a festival. The separation is wide enough to be encouraging, but the
   synopsis KPI (≥90% under festival lighting) remains unproven.
3. **Inference time has not been measured on a physical device**, so the <200 ms
   per-face target (NFR-03) is not yet evidenced.
4. **NFR-10 drift:** the synopsis states Android 5.0+ (API 21); the project sets
   `minSdk = 24`. The synopsis figure needs correcting.
5. **Volunteer Google sign-in is now verified end to end** (§8.1) — this
   limitation is closed for the app. The remaining sign-in gaps are:
   - The **email/password** and **guest** routes are implemented but not yet
     exercised on a device; each needs its provider enabled in the Firebase
     console.
   - The **kiosk** authorisation path is still untested, and locks out
     *everyone* until at least one `allowedOfficers/{email}` document exists and
     the rules and function are deployed.

---

## 10. Next week (Week 4)

1. Run the app on physical devices and record real cosine scores and per-face
   inference times — closing limitations 2 and 3.
2. Deploy the Cloud Functions and confirm server-side embedding — closing
   limitation 1.
3. Finish the sign-in configuration and test both flows end to end: register the
   SHA-1 for Google sign-in on the app, and add an officer to `allowedOfficers`
   for the kiosk — closing limitation 5.
4. Multi-device mesh trial: verify multi-hop relay and TTL behaviour with three
   or more phones and no internet.
4. Begin the optional Raspberry Pi node if the above is stable.
