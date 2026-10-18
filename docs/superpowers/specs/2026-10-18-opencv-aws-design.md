# Nungil for the OpenCV AI Competition 2026: design

Date: 2026-10-18. Deadline: 2026-10-26 23:59 Pacific (2026-10-27 06:59 UTC). Eight days.

## Intent

Nungil is a finished on-device scene assistant for blind users (see README). The OpenCV AI Competition 2026,
powered by AWS, has two hard requirements Nungil does not meet today:

- "Use OpenCV 5 for substantive image or video analysis."
- "Run a meaningful component on AWS."

This project makes Nungil qualify without changing what it is: the phone keeps working with no internet, in
English and Korean, by voice. OpenCV 5 takes over three real steps of the on-device pipeline, and AWS adds one
optional online ability, "look closer", that the user asks for by voice.

The entry targets the overall awards and the Agentic Vision award (Find and Walk already turn what the camera
sees into the next beep, vibration or sentence; the entry documents and traces that loop). The COOL award
(Cloud-Optimized OpenCV Library) is not targeted: its definition could not be read (the Devpost rules page was
not reachable from this machine; requirements come from the Devpost summary and a participant's verbatim copy).

## Goals

1. OpenCV 5.0.0.1 (Maven Central `org.opencv:opencv:5.0.0.1`) does substantive work on every frame:
   - **Colour of detected objects** (Live, Full scan, Find): white balance, HSV and colour naming over the whole
     object region with OpenCV (`cvtColor`, `mean`, `multiply`, `inRange`, `countNonZero`) instead of a 24 x 24
     point sample.
   - **Item outlines** (saved objects): the segmenter's mask is cleaned with OpenCV morphology and reduced to the
     connected component under the user's finger, so stray blobs no longer become part of a saved object.
   - **Walk-mode depth**: ARCore's depth image is median-filtered with OpenCV before the floor and wall logic reads
     it (the logs show 10-20 cm wobble on a plain floor).
2. An AWS component the user can feel: **"look closer"** ("Eye, look closer" / "눈길아 자세히 봐"). The phone sends
   the last frame to a Lambda function on Graviton (arm64). The function runs OpenCV 5 (Python) to upright,
   crop, enhance and QR-decode the picture, then asks Amazon Bedrock (Nova Lite) for a two-sentence spatial
   description or a verbatim reading of text, in the user's language, and the phone speaks it. Offline, Nungil
   says so and keeps working.
3. **Judge access**: the same Lambda serves a one-page web demo (upload a photo, ask, read the answer), which is
   the "working web endpoint" the submission requires.
4. **Agentic evidence**: a decision trace (JSONL) of perception -> decision -> action for Find, Walk and look
   closer, pulled off the phone and plotted for the report.
5. **Submission package**: technical report, architecture diagram (input / OpenCV layer / perception / agent /
   AWS / action), evaluation evidence (existing COCO and LFW benchmarks plus new colour, mask, depth and cloud
   measurements), README with pinned dependencies and build, deploy and test instructions, a video of at most
   five minutes, and the Devpost form text.

## Non-goals

- No change to detectors, face recognition, OCR, barcode reading, routing, or any spoken behaviour that exists
  today, other than the three OpenCV steps above.
- The item-colour path (`ItemColor`, colour of a saved object) stays pure Kotlin: its rules differ from scan's
  and the week is short.
- No cloud storage of images. The Lambda keeps nothing; CloudWatch gets latencies and sizes only.
- No new Android dependencies other than OpenCV. HTTP is `HttpURLConnection`.
- No COOL award work.

## Anti-goals

- An offline feature that now needs the network, or that breaks when the OpenCV native library fails to load.
  Every OpenCV step has the old pure-Kotlin path behind it, chosen when `OpenCVLoader.initLocal()` fails.
- A thin "bolt-on": OpenCV calls that do no analysis. Each step replaces or improves a measured behaviour, and the
  report shows the measurement.
- Personal data in the public repo (face photos are git-ignored) or in the cloud (no image storage, no user id).

## Constraints

- Repo `androidIkrom/nungil-opencv` (public, AGPL-3.0, history from 2026-09-24, inside the build phase). Work on
  a branch, `main` only through a pull request (CI runs `testDebugUnitTest assembleDebug -PskipModels`).
- Tests: JVM unit tests for pure code; OpenCV-dependent code is tested with androidTest on the **emulator**, over
  the real photos already in `app/src/androidTest/assets` (`desks/`, `photos/`). The phone is the user's; one
  live check per feature at the end, on the user's word.
- Laptop: about 2 GB free RAM, one emulator, no parallel Gradle builds. `JAVA_HOME` is Android Studio's `jbr`.
- AWS: the user creates the account, installs AWS CLI v2, runs `aws configure`, and enables Bedrock model access
  for Nova Lite in `us-east-1`. Everything else is a script.
- Python for the Lambda: 3.12 on Lambda, local 3.13 for tests. `opencv-python-headless==5.0.0.93`.
- Commit messages: plain English, no AI co-author line.

## How it is made (decided)

- **Android**: package `com.nungil.cv` holds every OpenCV call (`Cv`, `CvColor`, `CvMask`, `CvDepth`). Core
  stays pure: new pure pieces (`ColorBands`, `ColorVote.decide`, `WhiteBalance.gainsOf`, `ItemMask.of(inside)`,
  `CloudJson`, `CloudPhrases`, `TraceLine`) are JVM-tested. `abiFilters arm64-v8a, x86_64` (phone and emulator).
- **Cloud**: `cloud/` in the repo. `app/prep.py` (OpenCV), `app/describe.py` (Bedrock Converse API,
  `amazon.nova-lite-v1:0`), `app/handler.py` (Lambda Function URL: `POST /look`, `GET /`), `web/index.html`,
  `deploy.sh` (AWS CLI, idempotent, arm64, Python 3.12, 1024 MB, 30 s), `tests/` (pytest, fake Bedrock client).
  A shared secret header `x-nungil-key` guards `/look`; the key is given to judges in the Devpost testing notes.
- **App to cloud**: `BuildConfig.NUNGIL_CLOUD_URL` and `NUNGIL_CLOUD_KEY` from the git-ignored
  `local.properties`, like `ORS_API_KEY`. Frame sent as JPEG, long side 1280, quality 80, with the Find target
  box when there is one. Timeout 15 s.
- **Voice**: `VoiceCommand.LookCloser` -> `CameraAction.LookCloser` -> `FrameAnswers.closer(frame, lang)`, the
  same route as "what is this".
- **Trace**: `core/trace/TraceEvent` + `TraceLine.json`; app `DecisionTrace` appends to `files/trace/*.jsonl`;
  `tools/trace/plot.py` draws the figure.
- **Report**: `docs/hackathon/report.md` (Markdown in the repo; the PDF is a browser print), `architecture.svg`,
  `evaluation.md`, `video-script.md`, `devpost.md`.

## Timeline (my call)

| Day | Work |
|---|---|
| 10-18, 19 | OpenCV dependency, colour path, parity test |
| 10-20 | Mask clean-up, depth filter |
| 10-21 | Decision trace; Lambda code and tests |
| 10-22 | Deploy; app client, voice command, phrases |
| 10-23 | Evaluation runs, report, diagram |
| 10-24 | README, Devpost text, video script, video |
| 10-25, 26 | Buffer, live check, submit |

## Left to the builder

Exact OpenCV kernel sizes within the stated ranges, Bedrock prompt wording (the behaviour is fixed: two
sentences, spatial words, verbatim text, no identity guesses), the web page's look, the figure styles.
