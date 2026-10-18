# OpenCV 5 + AWS for Nungil Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Nungil qualify for the OpenCV AI Competition 2026: OpenCV 5 does three substantive on-device steps, an AWS Lambda on Graviton answers "look closer", and the submission package (report, diagram, evaluation, video script, Devpost text) is in the repo.

**Architecture:** All OpenCV calls on Android live in `com.nungil.cv`, each with the old pure-Kotlin path as fallback when the native library does not load. Core stays pure and JVM-tested; OpenCV code is tested with androidTest on the emulator over the real photos in `app/src/androidTest/assets`. The cloud is one Python Lambda (`cloud/`) that preprocesses with OpenCV 5 and asks Bedrock Nova Lite, reached from the phone by voice through the existing camera-command route.

**Tech Stack:** Kotlin, OpenCV 5.0.0.1 Android AAR, LiteRT, ARCore, CameraX; Python 3.12, `opencv-python-headless==5.0.0.93`, boto3 (Lambda runtime), AWS Lambda arm64 + Function URL, Amazon Bedrock Converse API.

**Spec:** `docs/superpowers/specs/2026-10-18-opencv-aws-design.md`

## Global Constraints

- OpenCV on Android: `implementation 'org.opencv:opencv:5.0.0.1'`, exactly. Python: `opencv-python-headless==5.0.0.93`.
- Every OpenCV step has the pre-existing pure path behind it, taken when `Cv.ensure()` is false. Spoken behaviour is otherwise unchanged.
- No new Android dependency other than OpenCV. HTTP through `HttpURLConnection`.
- Every new spoken phrase exists in English and Korean (`Lang.EN`, `Lang.KO`).
- The Lambda stores no image and logs no image bytes; `x-nungil-key` guards `POST /look`.
- Lambda: `--architectures arm64 --runtime python3.12 --memory-size 1024 --timeout 30`, region `us-east-1`, model id from env `MODEL_ID`, default `amazon.nova-lite-v1:0`.
- Tests on the emulator only (`adb -s emulator-5554`); never install on the phone unless the user asks. `JAVA_HOME` = Android Studio `jbr`. One Gradle build at a time.
- Branch `opencv` off `main`; `main` only via PR. Commit messages: plain English, no AI co-author line.
- `-PskipModels` CI build must keep passing (the OpenCV AAR downloads from Maven Central, fine in CI).

## Review Focus

1. Cloud not configured (`NUNGIL_CLOUD_URL` blank): "look closer" speaks the not-set-up phrase and makes no network call. Test: Task 9.
2. Airplane mode: "look closer" speaks the offline phrase within about a second; `UnknownHostException` never reaches the user. Test: Task 9.
3. OpenCV native library missing (an ABI not in `abiFilters`, or `initLocal()` false): colour, mask and depth fall back to the old paths with identical results. Test: Task 3, 4, 5 (fallback assertions).
4. A depth image that is all zeros (ARCore "could not measure"): `CvDepth.denoise` returns all zeros, no exception, and Walk says what it said before. Test: Task 5.
5. A slow cloud (over 15 s) or a 5xx: the failed phrase is spoken, the trace records it, and "stop" still silences speech. Test: Task 9 (fake server sleeping / returning 500).

---

### Task 1: OpenCV 5 dependency and loader

**Files:**
- Modify: `app/build.gradle` (dependencies block near line 109; `defaultConfig` near line 21)
- Create: `app/src/main/java/com/nungil/cv/Cv.kt`
- Test: `app/src/androidTest/java/com/nungil/cv/CvSmokeTest.kt`

**Interfaces:**
- Produces: `object Cv { fun ensure(): Boolean; val version: String?; @VisibleForTesting var forceOff: Boolean }`. `ensure()` calls `org.opencv.android.OpenCVLoader.initLocal()` once (synchronized, result cached), logs `Core.VERSION` with tag `NungilCv`, returns false and logs the throwable when the native library is missing, and returns false while `forceOff` is true (tests of the fallback paths). `version` is `Core.VERSION` after a true `ensure()`, else null.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class CvSmokeTest {
    @Test fun loadsOpenCv5() {
        assertTrue(Cv.ensure())
        assertTrue(Cv.version!!.startsWith("5."))
        val m = Mat.zeros(4, 4, CvType.CV_8UC1)
        assertEquals(16, m.total().toInt())
    }
}
```

- [ ] **Step 2: Add the dependency and ABI filters**

In `app/build.gradle`: `implementation 'org.opencv:opencv:5.0.0.1'`; in `defaultConfig`: `ndk { abiFilters 'arm64-v8a', 'x86_64' }`. Record the APK size before and after (`ls -l app/build/outputs/apk/debug/app-debug.apk`) for the report.

- [ ] **Step 3: Run the test on the emulator, expect it to fail (class `Cv` missing), then implement `Cv`**

Build and run as the memory notes say: `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest`, `adb -s emulator-5554 install -r` both APKs, `MSYS_NO_PATHCONV=1 adb -s emulator-5554 shell am instrument -w -e class com.nungil.cv.CvSmokeTest com.nungil.test/androidx.test.runner.AndroidJUnitRunner`.

- [ ] **Step 4: Run again, expect `OK (1 test)`; run `./gradlew.bat testDebugUnitTest -PskipModels` to see nothing else broke**

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle app/src/main/java/com/nungil/cv/Cv.kt app/src/androidTest/java/com/nungil/cv/CvSmokeTest.kt
git commit -m "Add OpenCV 5.0.0.1 and a loader that falls back when the native library is missing"
```

---

### Task 2: Colour bands and the shared vote rule (pure)

**Files:**
- Create: `app/src/main/java/com/nungil/core/scan/ColorBands.kt`
- Modify: `app/src/main/java/com/nungil/core/scan/ColorVote.kt:56-82` (`vote`, `counts`)
- Modify: `app/src/main/java/com/nungil/core/scan/WhiteBalance.kt:13` (`gains`)
- Test: `app/src/test/java/com/nungil/core/scan/ColorBandsTest.kt`, `ColorVoteTest.kt` (exists; add cases)

**Interfaces:**
- Produces: `object ColorBands { data class Band(val name: ColorName, val h0: Int, val h1: Int, val s0: Int, val s1: Int, val v0: Int, val v1: Int); val bands: List<Band>; fun nameOf(h: Int, s: Int, v: Int): ColorName? }`. Units are OpenCV 8-bit HSV: h 0..179, s 0..255, v 0..255, bounds inclusive. The bands are disjoint and cover the cube: `nameOf` is `ColorMapper.nameFromHsv(h * 2f, s / 255f, v / 255f, false)` expressed as boxes (black v < 0.2; white/gray s < 0.15 split at v 0.8; red/pink; orange/brown split at v 0.6; yellow, green, blue, purple, pink by hue; light red is pink).
- Produces: `ColorVote.decide(counts: IntArray, frameIsDark: Boolean): ColorName?` holding the rule now inside `vote` (chromatic name wins once chromatic pixels are at least `MIN_CHROMATIC_SHARE` of named pixels); `vote` becomes `decide(counts(pixels, gains), frameIsDark)`. `counts` layout: index `ColorName.ordinal`, as today.
- Produces: `WhiteBalance.gainsOf(meanR: Float, meanG: Float, meanB: Float): FloatArray`, the gray-world formula; `gains(pixels)` computes the three means and calls it.

- [ ] **Step 1: Write the failing tests**

```kotlin
class ColorBandsTest {
    @Test fun bandsAgreeWithColorMapperAwayFromEdges() {
        var checked = 0
        for (h in 0 until 180 step 1) for (s in 0..255 step 5) for (v in 0..255 step 5) {
            val want = ColorMapper.nameFromHsv(h * 2f, s / 255f, v / 255f, false)
            val got = ColorBands.nameOf(h, s, v)
            if (ColorBands.nearEdge(h, s, v)) continue   // within 1 unit of any bound: rounding differs, not checked
            assertEquals("h=$h s=$s v=$v", want, got); checked++
        }
        assertTrue(checked > 100_000)
    }
    @Test fun bandsAreDisjointAndCoverTheCube() { /* every (h,s,v) on a step-3 grid matches exactly one band */ }
}
// ColorVoteTest additions
@Test fun decideMatchesVote() { /* for 3 pixel arrays used by existing tests: ColorVote.vote(p, g, false) == ColorVote.decide(ColorVote.counts(p, g), false) */ }
// WhiteBalanceTest (create if absent)
@Test fun gainsOfMatchesGainsOnUniformPixels() { /* pixels all 0xFF8040C0: gains(pixels) contentEquals gainsOf(128f, 64f, 192f) within 1e-4 */ }
```

`ColorBands.nearEdge(h, s, v): Boolean` is a test helper in the object: true when h, s or v is within 1 unit of any band bound.

- [ ] **Step 2: Run `./gradlew.bat testDebugUnitTest -PskipModels --tests '*ColorBandsTest*' --tests '*ColorVoteTest*' --tests '*WhiteBalanceTest*'`, expect compile failure**

- [ ] **Step 3: Implement `ColorBands`, `ColorVote.decide`, `WhiteBalance.gainsOf`**

- [ ] **Step 4: Run the same command, expect all PASS; run the whole unit suite once**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nungil/core/scan app/src/test/java/com/nungil/core/scan
git commit -m "Express the colour names as HSV bands and share the vote rule, so OpenCV can count them"
```

---

### Task 3: OpenCV colour for scan (Live, Full, Find)

**Files:**
- Create: `app/src/main/java/com/nungil/cv/CvColor.kt`
- Modify: `app/src/main/java/com/nungil/scan/ColorSampler.kt` (`frameLight`, `colorOf`, `explain`)
- Test: `app/src/androidTest/java/com/nungil/cv/CvColorParityTest.kt`

**Interfaces:**
- Consumes: `Cv.ensure()`, `ColorBands.bands`, `ColorVote.decide`, `WhiteBalance.gainsOf`, `ColorVote.sampleRegion`, `ColorVote.DARK_FRAME_V`.
- Produces: `object CvColor { fun frameLight(bitmap: Bitmap): ColorSampler.FrameLight; fun counts(bitmap: Bitmap, region: Box, gains: FloatArray): IntArray }`.
  - `frameLight`: `Utils.bitmapToMat` (RGBA) -> `cvtColor(RGBA2RGB)`; `Core.mean` -> `WhiteBalance.gainsOf`; `cvtColor(RGB2HSV)` and `Core.mean` of V; `isDark = meanV / 255f < DARK_FRAME_V`.
  - `counts`: ROI `region` in pixels (clamped, at least 1 x 1), `Core.multiply(roi, Scalar(gR, gG, gB))` (saturating 8U), `cvtColor(RGB2HSV)`, one `Core.inRange` + `Core.countNonZero` per `ColorBands.Band`, summed into `IntArray(ColorName.entries.size)` by ordinal. All pixels of the region count, not a grid.
- `ColorSampler.frameLight` and `colorOf` use `CvColor` when `Cv.ensure()`, else the current code; `colorOf` = `ColorVote.decide(CvColor.counts(bitmap, ColorVote.sampleRegion(box), light.gains), light.isDark)`. `explain` adds `"; cv"` or `"; grid"` to say which path ran.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class CvColorParityTest {
    @get:Rule val host = ActivityScenarioRule(ReplayHostActivity::class.java)
    /** Over desks/*.jpg and 5 fixed boxes each: OpenCV's name equals the grid's name on at least 80% of boxes
     *  where either names a colour; gains agree within 0.05; logs agreement, per-box ms for both (tag NungilCv). */
    @Test fun opencvColourAgreesWithGridSampling() { ... assertTrue(agreement >= 0.8) }
    /** With Cv forced off (Cv.forceOff = true in debug), ColorSampler.colorOf returns exactly the grid answer. */
    @Test fun fallsBackToGridWhenOpenCvIsOff() { ... }
}
```

Boxes: centre 50 %, and the four quadrants' centre 50 %. `Cv.forceOff` comes from Task 1.

- [ ] **Step 2: Run on the emulator, expect compile failure, then implement `CvColor` and the `ColorSampler` switch**

- [ ] **Step 3: Run again, expect `OK (2 tests)`; copy the agreement and the two timings from logcat into `docs/hackathon/evaluation.md` (create the file with a "Colour" section)**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/nungil/cv/CvColor.kt app/src/main/java/com/nungil/cv/Cv.kt app/src/main/java/com/nungil/scan/ColorSampler.kt app/src/androidTest/java/com/nungil/cv/CvColorParityTest.kt docs/hackathon/evaluation.md
git commit -m "Scan names colours with OpenCV over the whole object region, with the grid sampler as fallback"
```

---

### Task 4: Mask clean-up with OpenCV

**Files:**
- Create: `app/src/main/java/com/nungil/cv/CvMask.kt`
- Modify: `app/src/main/java/com/nungil/core/items/ItemMask.kt:142` (add an `of(inside, …)` factory)
- Modify: `app/src/main/java/com/nungil/items/ItemSight.kt:52`, `app/src/main/java/com/nungil/items/ItemFinder.kt:68`
- Test: `app/src/test/java/com/nungil/core/items/ItemMaskTest.kt` (exists; add one case), `app/src/androidTest/java/com/nungil/cv/CvMaskTest.kt`

**Interfaces:**
- Produces (core): `ItemMask.of(inside: BooleanArray, width: Int, height: Int): ItemMask?` — same `MIN_COVER`/`MAX_COVER` gate as the confidence overload, cover = share of true.
- Produces (cv): `object CvMask { fun clean(confidence: FloatArray, width: Int, height: Int, x: Float, y: Float): ItemMask?; fun components(inside: BooleanArray, width: Int, height: Int): Int }`.
  - `clean`: threshold at `ItemMask.INSIDE` with the polarity `ItemMask.pointIsHigh` gives (make it `internal`), 8UC1 Mat; `Imgproc.morphologyEx(MORPH_OPEN, 3x3 ellipse)` then `MORPH_CLOSE` same kernel; `Imgproc.connectedComponents` (8-connectivity); keep the label at the touched pixel, or, when that pixel is background after opening, the label of the largest component; `ItemMask.of(inside, …)`.
  - `components` counts labels except background (for tests and the report).
- `ItemSight.see` and `ItemFinder` call `CvMask.clean(...)` when `Cv.ensure()`, else `ItemMask.of(confidence, …)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ItemMaskTest
@Test fun ofInsideKeepsCoverGate() { /* 10x10 all false -> null; 10x10 with 30 true -> cover 0.3 */ }
// CvMaskTest (emulator)
@Test fun cleanedMaskIsOneComponentAndCloseToRaw() {
    // desks/*.jpg x points (0.5,0.5),(0.3,0.6),(0.7,0.4) through ItemSegmenter: for every non-null pair,
    // CvMask.components(cleaned.inside) == 1, IoU(cleaned, raw) >= 0.85 on average; log removed share and ms.
}
@Test fun fallsBackWhenOpenCvIsOff() { /* Cv.forceOff: ItemSight path gives the raw ItemMask.of result */ }
```

- [ ] **Step 2: Run the unit test, expect compile failure; implement `ItemMask.of(inside, …)`; run, expect PASS**

- [ ] **Step 3: Run `CvMaskTest` on the emulator, expect failure; implement `CvMask` and the two call sites; run, expect `OK (2 tests)`**

- [ ] **Step 4: Add the "Item mask" section to `docs/hackathon/evaluation.md` (components before/after, IoU, ms)**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nungil/cv/CvMask.kt app/src/main/java/com/nungil/core/items/ItemMask.kt app/src/main/java/com/nungil/items/ItemSight.kt app/src/main/java/com/nungil/items/ItemFinder.kt app/src/test/java/com/nungil/core/items/ItemMaskTest.kt app/src/androidTest/java/com/nungil/cv/CvMaskTest.kt docs/hackathon/evaluation.md
git commit -m "Clean the item mask with OpenCV morphology and keep only the touched component"
```

---

### Task 5: Depth median filter with OpenCV

**Files:**
- Create: `app/src/main/java/com/nungil/cv/CvDepth.kt`
- Modify: `app/src/main/java/com/nungil/walk/WalkVision.kt:144-146`
- Test: `app/src/androidTest/java/com/nungil/cv/CvDepthTest.kt`

**Interfaces:**
- Produces: `object CvDepth { fun denoise(depthMm: ShortArray, width: Int, height: Int): ShortArray }` — 16UC1 Mat, `Imgproc.medianBlur(ksize = 3)`, then every pixel that was 0 in the input is 0 in the output (no invented measurements). Returns a new array; input untouched.
- `WalkVision.submit`: `val depth = if (Cv.ensure()) CvDepth.denoise(raw, input.depthWidth, input.depthHeight) else raw` before `DepthGrid.fill`. The recorder still gets the raw frame.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class CvDepthTest {
    @Test fun removesSpikesKeepsEdgesAndZeros() {
        // 160x90 plane at 1500 mm with 5% random spikes in 300..6000 (seed 1): after denoise, >= 99% of
        // non-zero pixels within 1500 +- 20.
        // Left half 1500 / right half 2500: no output value in 1600..2400.
        // 3% zeros: those stay 0.
        // All-zero image: output all zero, no exception.
    }
}
```

- [ ] **Step 2: Run on the emulator, expect compile failure; implement `CvDepth` and the `WalkVision` switch; run, expect `OK (1 test)`**

- [ ] **Step 3: If `files/walk-rec/*` recordings exist on the emulator or in the repo's tools, replay one through `GroundProfile.rows` with and without `denoise` and note the change in `FloorReading`s in `evaluation.md` ("Depth" section). Otherwise record the synthetic numbers.**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/nungil/cv/CvDepth.kt app/src/main/java/com/nungil/walk/WalkVision.kt app/src/androidTest/java/com/nungil/cv/CvDepthTest.kt docs/hackathon/evaluation.md
git commit -m "Walk filters ARCore depth with an OpenCV median before reading floors and walls"
```

---

### Task 6: Decision trace

**Files:**
- Create: `app/src/main/java/com/nungil/core/trace/TraceEvent.kt`, `app/src/main/java/com/nungil/trace/DecisionTrace.kt`, `tools/trace/plot.py`
- Modify: `app/src/main/java/com/nungil/search/SearchCameraFragment.kt` (around lines 227-240, where the pulse and the centred buzz are decided), `app/src/main/java/com/nungil/walk/WalkVision.kt:223` (`deliver`)
- Test: `app/src/test/java/com/nungil/core/trace/TraceLineTest.kt`

**Interfaces:**
- Produces (core): `data class TraceEvent(val atMs: Long, val mode: String, val seen: String, val decision: String, val action: String)`; `object TraceLine { fun json(e: TraceEvent): String }` — one JSON object per line, keys `at, mode, seen, decision, action`, strings escaped (quotes, backslash, newline).
- Produces (app): `class DecisionTrace private constructor(dir: File) { fun record(e: TraceEvent); companion object { fun shared(context: Context): DecisionTrace } }` — appends to `<filesDir>/trace/<yyyy-MM-dd>.jsonl` on a single-thread executor; never throws to the caller.
- Modes: `"find"` (seen: target box or "none", decision: pulse interval ms or "lost", action: "beep", "buzz", "say Lost it"), `"walk"` (seen: zone distances and floor reading, decision: alert kind, action: spoken text or "none"), `"cloud"` (Task 9).
- `tools/trace/plot.py <file.jsonl> <out.png>`: a timeline, one row per mode, markers coloured by action.

- [ ] **Step 1: Write the failing test**

```kotlin
class TraceLineTest {
    @Test fun oneLineEscapedJson() {
        val s = TraceLine.json(TraceEvent(12, "find", "bag 0.4,0.5", "pulse 300", "say \"Lost it\"\nnow"))
        assertEquals("""{"at":12,"mode":"find","seen":"bag 0.4,0.5","decision":"pulse 300","action":"say \"Lost it\"\nnow"}""", s)
        assertFalse(s.contains('\n'))
    }
}
```

- [ ] **Step 2: Run, expect compile failure; implement `TraceEvent`, `TraceLine`; run, expect PASS**

- [ ] **Step 3: Implement `DecisionTrace` and the two hooks; build, open Find on the emulator against a desk photo (ReplayHostActivity or the app), `adb -s emulator-5554 exec-out run-as com.nungil cat files/trace/<date>.jsonl | head`, expect find lines**

- [ ] **Step 4: Write `tools/trace/plot.py` (matplotlib from `D:\projects\nungil-bench\venv`), run it on the pulled file, expect a PNG**

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/nungil/core/trace app/src/main/java/com/nungil/trace app/src/main/java/com/nungil/search/SearchCameraFragment.kt app/src/main/java/com/nungil/walk/WalkVision.kt app/src/test/java/com/nungil/core/trace tools/trace/plot.py
git commit -m "Record a perception-to-action trace for Find and Walk, with a plot script"
```

---

### Task 7: Cloud Lambda code (Python)

**Files:**
- Create: `cloud/app/prep.py`, `cloud/app/describe.py`, `cloud/app/handler.py`, `cloud/web/index.html`, `cloud/requirements.txt`, `cloud/requirements-dev.txt`, `cloud/README.md`
- Test: `cloud/tests/test_prep.py`, `cloud/tests/test_describe.py`, `cloud/tests/test_handler.py`, `cloud/tests/fixtures/` (two JPEGs copied from `app/src/androidTest/assets/photos`, one with a QR from `tools/reader-photos` if present, else drawn with `cv2.QRCodeEncoder`)

**Interfaces:**
- `prep.prepare(jpeg: bytes, box: tuple[float, float, float, float] | None) -> tuple[bytes, dict]`: `cv2.imdecode`; resize so the long side is at most 1280 (`INTER_AREA`); when `box` is given (left, top, right, bottom in 0..1) crop it grown by 15 % each side and clamped; `dark = mean L of LAB < 90`, and when dark apply CLAHE (clipLimit 2.0, tiles 8x8) on L; `qr = cv2.QRCodeDetector().detectAndDecode(img)[0] or None`; return JPEG (quality 85) and `{"qr": qr, "size": [w, h], "dark": dark, "cropped": box is not None}`.
- `describe.describe(image_jpeg: bytes, question: str, lang: str, facts: dict, client=None) -> str`: Bedrock Converse (`client or boto3.client("bedrock-runtime")`), model `os.environ.get("MODEL_ID", "amazon.nova-lite-v1:0")`, system prompt fixing the behaviour: answer in `lang` ("en" or "ko"), at most two sentences, spatial words (left, right, ahead, near, far), read visible text verbatim, never guess who a person is, say "I cannot tell" when unsure; `facts["qr"]` is appended as "A QR code says: …" when present. `maxTokens 200, temperature 0.2`.
- `handler.handler(event, context) -> dict` (Function URL payload 2.0): `GET /` -> 200 `text/html` with `web/index.html`; `POST /look` -> 403 when header `x-nungil-key` != `os.environ["NUNGIL_KEY"]`; 413 when the decoded image is over 4 MB; 400 on bad JSON or missing `image`; else `{"answer": str, "qr": str|None, "ms": int, "model": str}`; any other route 404. Logs one line per request: route, status, ms, bytes in, never the image.
- `web/index.html`: file input, question box, language select (en/ko), key field, "Ask" button; posts to `./look`; shows the answer and ms.

- [ ] **Step 1: Write the failing tests**

```python
# test_prep.py
def test_resizes_long_side_to_1280(): ...            # 4000x3000 fixture -> size[0] == 1280
def test_crop_grows_box_by_15_percent(): ...        # box (0.4,0.4,0.6,0.6) on 1000x1000 -> width 260 +- 2
def test_dark_image_gets_clahe_and_flag(): ...      # fixture * 0.2 -> dark True, mean L rises
def test_reads_qr(): ...                            # qr fixture -> qr == "https://nungil.example/test"
# test_describe.py
class FakeBedrock:  # records the request, returns {"output": {"message": {"content": [{"text": "Two chairs ahead."}]}}}
def test_sends_image_language_and_qr_fact(): ...    # request has image bytes, "ko" in system text, "QR code says" when qr
def test_returns_text(): ...
# test_handler.py
def test_get_root_serves_page(): ...
def test_look_rejects_bad_key(): ...                # 403
def test_look_rejects_large_image(): ...            # 413 with a 5 MB base64 blob
def test_look_answers(monkeypatch): ...             # describe patched -> 200, answer, ms int
```

- [ ] **Step 2: Create `cloud/.venv` (`python -m venv cloud/.venv`), install `requirements-dev.txt` (`opencv-python-headless==5.0.0.93`, `numpy`, `boto3`, `pytest`), run `cloud/.venv/Scripts/python -m pytest cloud/tests -q`, expect import errors**

- [ ] **Step 3: Implement `prep.py`, `describe.py`, `handler.py`, `web/index.html`; pin `requirements.txt` to the installed `numpy` version**

- [ ] **Step 4: Run pytest again, expect all PASS**

- [ ] **Step 5: Commit**

```bash
git add cloud
git commit -m "Add the look-closer Lambda: OpenCV 5 preprocessing, Bedrock description, judge web page"
```

---

### Task 8: Deploy script and the live endpoint

**Files:**
- Create: `cloud/deploy.sh`, `cloud/smoke.sh`
- Modify: `cloud/README.md`

**Interfaces:**
- `cloud/deploy.sh` (bash, AWS CLI v2, idempotent): builds `build/layer.zip` from `pip download --platform manylinux2014_aarch64 --python-version 3.12 --only-binary=:all: -r requirements.txt` unpacked under `python/`; `build/function.zip` from `app/` and `web/`; creates or reuses role `nungil-look-role` (trust `lambda.amazonaws.com`, managed `AWSLambdaBasicExecutionRole`, inline `bedrock:InvokeModel` on `*`); publishes layer `nungil-opencv5`; creates or updates function `nungil-look` with the Global Constraints settings and env `NUNGIL_KEY` (from `$NUNGIL_KEY`, generated with `openssl rand -hex 16` when unset and printed once) and `MODEL_ID`; creates or reuses a Function URL (`--auth-type NONE`); prints the URL.
- `cloud/smoke.sh <url> <key>`: posts `tests/fixtures/desk.jpg` with question "What is in front of me?" and prints the answer and ms; exits 1 on non-200.

User steps (cannot be scripted): create the AWS account, install AWS CLI v2, `aws configure` (region `us-east-1`), enable Bedrock model access for Nova Lite in the console.

- [ ] **Step 1: Write `deploy.sh` and `smoke.sh`; run `bash -n` on both**

- [ ] **Step 2: Ask the user to do the user steps; then run `bash cloud/deploy.sh`, expect a printed URL**

- [ ] **Step 3: Run `bash cloud/smoke.sh <url> <key>`, expect a one or two sentence answer and `ms` under 8000; open `<url>/` in a browser, upload a photo, expect an answer**

- [ ] **Step 4: Put the URL and key in `D:\nungil-opencv\local.properties` as `NUNGIL_CLOUD_URL` and `NUNGIL_CLOUD_KEY` (git-ignored); write the deploy and judge-access steps in `cloud/README.md` (no key in the repo)**

- [ ] **Step 5: Commit**

```bash
git add cloud/deploy.sh cloud/smoke.sh cloud/README.md
git commit -m "Deploy the look-closer Lambda on Graviton with one script"
```

---

### Task 9: "Look closer" in the app

**Files:**
- Create: `app/src/main/java/com/nungil/core/cloud/CloudJson.kt`, `app/src/main/java/com/nungil/core/cloud/CloudPhrases.kt`, `app/src/main/java/com/nungil/cloud/CloudLook.kt`
- Modify: `app/build.gradle:26` (two more `buildConfigField`s), `app/src/main/java/com/nungil/contract/VoiceCommand.kt`, `app/src/main/java/com/nungil/core/voice/VoiceCommandParser.kt:165` (before the `what` block), `app/src/main/java/com/nungil/core/ui/CameraCommandPolicy.kt:50`, `app/src/main/java/com/nungil/shell/MainActivity.kt:605`, `app/src/main/java/com/nungil/scan/FrameAnswers.kt`
- Test: `app/src/test/java/com/nungil/core/cloud/CloudJsonTest.kt`, `CloudPhrasesTest.kt`, `app/src/test/java/com/nungil/cloud/CloudLookTest.kt`, `app/src/test/java/com/nungil/core/voice/VoiceCommandParserTest.kt` (exists; add cases), `app/src/test/java/com/nungil/core/ui/CameraCommandPolicyTest.kt` (exists; add a case)

**Interfaces:**
- `VoiceCommand.LookCloser : VoiceCommand` (data object). Parser, checked before `what`: English `s.seq("look", "closer")`, `s.seq("closer", "look")`, `s.has("detail", "details")`, `s.seq("ask", "the", "cloud")`; Korean `s.ko("자세히", "더자세히", "클라우드")`.
- `CameraCommandPolicy.decide`: `VoiceCommand.LookCloser -> CameraAction.LookCloser` under the same readiness rules as `WhatIsThis`.
- `CameraAction.LookCloser : CameraAction` (data object); `MainActivity.onCameraCommand`: `CameraAction.LookCloser -> frameAnswers(screen)?.closer(screen.lastFrame(), lang)`.
- `object CloudJson { fun request(jpeg: ByteArray, question: String, lang: Lang, box: Box?): String; fun answer(json: String): CloudAnswer }`; `data class CloudAnswer(val text: String, val qr: String?, val ms: Long)`. Request keys `image` (base64), `question`, `lang` ("en"/"ko"), `box` ([l, t, r, b] or absent). Uses `org.json`.
- `object CloudPhrases { fun looking(lang): String; fun offline(lang): String; fun notSetUp(lang): String; fun failed(lang): String; fun question(lang): String }` — EN/KO: "Looking closer." / "자세히 볼게요."; "No internet. Nungil keeps working offline." / "인터넷이 없어요. 눈길은 오프라인으로 계속 작동해요."; "Cloud look is not set up." / "클라우드 보기가 설정되지 않았어요."; "The cloud did not answer." / "클라우드가 응답하지 않았어요."; question "Describe what is in front of me." / "앞에 무엇이 있는지 설명해 주세요."
- `class CloudLook(private val url: String, private val key: String, private val connectTimeoutMs: Int = 5_000, private val readTimeoutMs: Int = 15_000) { sealed interface Result { data class Ok(val answer: CloudAnswer) : Result; data object Offline : Result; data class Failed(val why: String) : Result }; fun ask(jpeg: ByteArray, question: String, lang: Lang, box: Box?): Result }` — `HttpURLConnection` POST to `"$url/look"` with `x-nungil-key`; `UnknownHostException`, `ConnectException`, `SocketTimeoutException` on connect -> `Offline`; read timeout or non-200 -> `Failed`.
- `FrameAnswers.closer(frame: VisionFrame?, lang: Lang)`: on its worker thread; `BuildConfig.NUNGIL_CLOUD_URL.isBlank()` -> speak `notSetUp`, return; else speak `looking`, make the bitmap as `what` does, scale so the long side is at most 1280, JPEG quality 80, `CloudLook.ask(..., CloudPhrases.question(lang), lang, box = null)`; speak the answer text (or `offline` / `failed`); `DecisionTrace.shared(context).record(TraceEvent(now, "cloud", "frame ${w}x${h} ${bytes}B", result kind, spoken text))`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// CloudJsonTest
@Test fun requestCarriesImageQuestionLangAndBox() { /* parse back with org.json: image decodes to the input bytes, lang "ko", box [0.1,0.2,0.3,0.4] */ }
@Test fun answerParsesTextQrAndMs() { /* {"answer":"A door ahead.","qr":null,"ms":1234} */ }
// CloudPhrasesTest
@Test fun everyPhraseExistsInBothLanguages() { /* all five, EN and KO, non-blank and different */ }
// VoiceCommandParserTest additions
@Test fun lookCloserPhrases() { /* "look closer", "more detail", "자세히 봐", "더 자세히 설명해 줘" -> LookCloser; "describe" alone still WhatIsThis */ }
// CameraCommandPolicyTest addition
@Test fun lookCloserRoutesLikeWhatIsThis() { ... }
// CloudLookTest: com.sun.net.httpserver.HttpServer on 127.0.0.1:0
@Test fun okAnswer() { /* server returns 200 JSON -> Result.Ok with text */ }
@Test fun badKeyIsFailed() { /* 403 -> Result.Failed */ }
@Test fun unknownHostIsOffline() { /* url "http://nungil.invalid" -> Result.Offline in under 2 s */ }
@Test fun slowServerIsFailed() { /* handler sleeps 2 s, readTimeoutMs = 500 -> Result.Failed */ }
```

- [ ] **Step 2: Run the unit tests, expect compile failures; implement contract, parser, policy, `CloudJson`, `CloudPhrases`, `CloudLook`, `BuildConfig` fields; run, expect PASS**

- [ ] **Step 3: Implement `CameraAction.LookCloser`, the `MainActivity` case and `FrameAnswers.closer`. `FrameAnswers` gets a constructor parameter `cloud: CloudLook? = CloudLook.fromBuildConfig()` (null when `NUNGIL_CLOUD_URL` is blank). Write and run `app/src/androidTest/java/com/nungil/scan/LookCloserTest.kt` on the emulator (ReplayHostActivity, a desk photo as the frame, `speak` collecting into a list):**
  - `notSetUpWhenNoCloud`: `cloud = null` -> the list is exactly `[CloudPhrases.notSetUp(Lang.EN)]`.
  - `offlineWithoutNetwork`: `adb -s emulator-5554 shell svc wifi disable && svc data disable` before the test (the test asserts `!isConnected` first) -> last phrase is `CloudPhrases.offline`.
  - `answersWhenDeployed`: skipped (`Assume`) when `BuildConfig.NUNGIL_CLOUD_URL` is blank; else the list is `[looking, <answer>]`, the answer is non-blank and not `failed`/`offline`, and `files/trace/<date>.jsonl` gained a `"mode":"cloud"` line.
  Re-enable the network afterwards (`svc wifi enable; svc data enable`).

- [ ] **Step 4: Run the full unit suite and `assembleDebug -PskipModels`, expect PASS**

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle app/src/main/java/com/nungil/contract/VoiceCommand.kt app/src/main/java/com/nungil/core app/src/main/java/com/nungil/cloud app/src/main/java/com/nungil/scan/FrameAnswers.kt app/src/main/java/com/nungil/shell/MainActivity.kt app/src/test
git commit -m "Look closer: the last frame goes to the Graviton Lambda and the answer is spoken; offline says so"
```

---

### Task 10: Evaluation evidence

**Files:**
- Create: `cloud/tests/latency.py`, `docs/hackathon/evaluation.md` (sections from Tasks 3-5 exist; add the rest), `docs/hackathon/figures/`
- Source data: `D:\projects\nungil-bench\results.json`, `D:\projects\nungil-bench\figures\` (detector COCO mAP and latency for 4 models CPU/GPU on 1 000 images; LFW face verification on 13 233 images, Infinix X6880)

**Interfaces:**
- `latency.py <url> <key>`: posts each JPEG in `cloud/tests/fixtures` and `app/src/androidTest/assets/photos` (10 or more), prints p50 and p95 ms, the answers, and a Markdown table.

- [ ] **Step 1: Write and run `latency.py`; paste the table and three sample answers (one Korean) into `evaluation.md` ("Cloud" section)**

- [ ] **Step 2: Copy the detector and face tables from `results.json` into `evaluation.md` ("Detectors", "Faces") with the phone and date, and the two matching figures into `docs/hackathon/figures/`**

- [ ] **Step 3: Pull one Find and one Walk trace from the emulator (or the phone, only if the user runs it), plot with `tools/trace/plot.py`, add to `figures/` and an "Agentic loop" section describing perception -> decision -> action with the figure, plus failure handling ("Lost it" after 3 s, "could not measure", offline phrase) and human control ("stop", "Eye stop")**

- [ ] **Step 4: Add "Known limitations" (depth noise, colour under coloured light, cloud needs network and a key, Nova may misread small text) and "Responsible use" (no image storage, no identity guesses, blind users verified the app only in the authors' tests; not a mobility aid replacement)**

- [ ] **Step 5: Commit**

```bash
git add cloud/tests/latency.py docs/hackathon
git commit -m "Evaluation evidence for the competition: colour, mask, depth, cloud latency, detectors, faces, traces"
```

---

### Task 11: Report, diagram, README, Devpost text, video script

**Files:**
- Create: `docs/hackathon/report.md`, `docs/hackathon/architecture.svg`, `docs/hackathon/video-script.md`, `docs/hackathon/devpost.md`
- Modify: `README.md` (new section "OpenCV 5 and AWS"; "Build" section with pinned versions and the test commands)

**Interfaces:**
- `report.md` sections, in this order: Problem; Who it serves; Architecture (embed `architecture.svg`); OpenCV 5 usage (the three steps, each with the measurement from `evaluation.md`); AWS deployment (Lambda arm64, Function URL, Bedrock, cost per 1 000 looks); Agentic loop; Evaluation (link each table); Limitations; Responsible use; Reproduce (build, test, deploy, judge access).
- `architecture.svg`: boxes left to right: Input (camera, ARCore depth, microphone) -> OpenCV 5 layer (colour, mask, depth; cloud prep) -> Perception (detectors, segmenter, faces, OCR) -> Agent (Find / Walk / Scan policies, trace) -> AWS (Lambda arm64, Bedrock) -> Action (speech, beeps, vibration, caption). Legible in light and dark (no transparent text on white).
- `video-script.md`: five minutes at most: 0:00 problem and user (30 s); 0:30 Live scan with colours (OpenCV) (45 s); 1:15 save an object, show the cleaned outline (45 s); 2:00 Walk with a step (45 s); 2:45 "look closer" online, then airplane mode (60 s); 3:45 judge web page (30 s); 4:15 architecture and evaluation slide (30 s); 4:45 close. Each shot lists what is said and what is on screen.
- `devpost.md`: the form fields (inspiration, what it does, how we built it, challenges, accomplishments, what we learned, what's next, built with), testing instructions for judges (web URL, where the key is given: in the Devpost private notes field, not the repo), repo link, video link placeholder.
- README "Build": `JAVA_HOME`, `./gradlew.bat assembleDebug` (models download at build), `testDebugUnitTest -PskipModels`, emulator androidTest commands, `cloud/deploy.sh`, `local.properties` keys, pinned versions (OpenCV 5.0.0.1, LiteRT 1.4.2, MediaPipe 1.0.0, ARCore 1.49.0, ML Kit versions, `opencv-python-headless==5.0.0.93`).

- [ ] **Step 1: Write `architecture.svg`; open it in a browser in light and dark, expect readable boxes and arrows**

- [ ] **Step 2: Write `report.md` from `evaluation.md` and the spec; every number cites its section; print to PDF from the browser as `docs/hackathon/report.pdf` (committed)**

- [ ] **Step 3: Write `video-script.md` and `devpost.md`; update README; run `git grep -n "NUNGIL_CLOUD_KEY\|x-nungil-key" -- ':!cloud/app' ':!app/src'` and expect no key value anywhere**

- [ ] **Step 4: Commit, open the PR `opencv` -> `main`, wait for CI green, merge**

```bash
git add README.md docs/hackathon
git commit -m "Competition report, architecture diagram, video script, Devpost text and build notes"
gh pr create --fill --base main --head opencv
```

- [ ] **Step 5: User steps: record the video on the phone (scrcpy or the phone's recorder; `ffmpeg` to trim to under five minutes), upload unlisted, fill the Devpost form from `devpost.md`, paste the key into the private testing notes, submit before 2026-10-26 23:59 Pacific**
