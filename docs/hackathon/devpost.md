# Devpost submission text

Form fields for https://opencv26.devpost.com/. Paste as written; fill the two placeholders.

## Project name

Nungil (눈길): an on-device scene assistant for blind users, with OpenCV 5 and one AWS "look closer"

## Elevator pitch (short)

A blind user holds the phone and turns around; Nungil says what is there, where, and what colour, in English or
Korean, offline. OpenCV 5 names the colours, cleans the object outlines and filters the depth; one voice command
sends a frame to a Graviton Lambda for a two-sentence description from Bedrock.

## Inspiration

A blind friend's question: "what is around me, right now, without a connection and without typing?" Phones carry
the camera, the depth sensor and the speaker; the missing part was software that speaks plainly and never guesses.

## What it does

Look around (a full 360° summary), Live scan, Find ("find my bag": beeps that quicken as the camera points at the
target), saved people and objects, a Reader for signs, QR and barcodes, and Walk mode with walls, steps, stairs,
drops and traffic lights from ARCore depth. Everything by voice, with the wake word "Eye" / "눈길아", in English
and Korean, with no internet. For this competition: OpenCV 5 does three substantive steps on every frame, and
"Eye, look closer" sends one frame (in Find, the target box only) to AWS for a fuller description when the phone
is online.

## How we built it

Kotlin on Android, CameraX, LiteRT on the GPU for detectors and the segmenter, ML Kit for faces, OCR and codes,
ARCore for depth. OpenCV 5.0.0.1 (Maven Central AAR) in `com.nungil.cv`: `cvtColor`, `mean`, `multiply`,
`inRange` for colour over the whole object region; `morphologyEx` and `connectedComponents` to clean the item
mask; `medianBlur` on the 16-bit depth image. Each step keeps the old pure-Kotlin path as a fallback. The cloud
half is one Python 3.12 Lambda on Graviton (arm64) behind a Function URL: OpenCV 5 (`opencv-python-headless`)
crops, resizes, lifts a dark frame with CLAHE and reads QR codes, then Amazon Bedrock (Nova Lite) answers in two
sentences in the user's language. The same Lambda serves the judges' web page. Every Find and Walk step is
recorded as a perception-to-action trace.

## Challenges we ran into

Keeping OpenCV's answers identical to the hand-written ones (the colour bands are proven equal to the old rules
on every 8-bit HSV value); a mask clean-up that never drops the item; a depth filter that never invents a
measurement where ARCore had none; making the online ability optional so nothing else ever waits for a network.

## Accomplishments that we're proud of

OpenCV colour agrees with the old sampler on 99 % of regions while judging every pixel; 30 test masks went from
7.7 pieces each to one with 97 % overlap kept; the offline path is untouched; a blind user can ask for more detail
by voice and is told plainly when there is no internet.

## What we learned

A median filter is enough to take depth's spikes without blurring a step edge; the connected component under the
user's finger is a better definition of "the item" than a threshold; a cloud model should be asked for two
sentences with spatial words, text verbatim and no identity guesses, and nothing else.

## What's next

A blind user's trial of Walk mode; a real-walk before/after of the depth filter; Korean OCR; the judges' page
in Korean.

## Built with

Kotlin, Android, OpenCV 5.0.0.1, LiteRT 1.4.2, MediaPipe Tasks 1.0.0, ML Kit, ARCore 1.49.0, Python 3.12,
opencv-python-headless 5.0.0.93, AWS Lambda (Graviton arm64), Lambda Function URL, Amazon Bedrock (Nova Lite),
CloudWatch Logs.

## Links

- Repository: https://github.com/androidIkrom/nungil-opencv
- Technical report: `docs/hackathon/report.md` in the repository
- Video: <UNLISTED VIDEO URL>
- Judges' web page: <FUNCTION URL> (the access key is in the private testing notes below)

## Testing instructions for judges (private notes field)

1. Open <FUNCTION URL> in a browser, choose a photo, type a question (or keep the default), pick English or
   Korean, paste the access key `<KEY>` and press Ask. The answer and the round-trip time appear below.
2. To build the app: see README "Build and run" (Android Studio or `./gradlew assembleDebug`; the models download
   at build time). To run the emulator tests of the OpenCV steps: `CvColorParityTest`, `CvMaskTest`, `CvDepthTest`,
   `DecisionTraceTest`, `LookCloserTest` (README "Tests").
3. The app's "look closer" needs `NUNGIL_CLOUD_URL` and `NUNGIL_CLOUD_KEY` in `local.properties`; without them it
   says "Cloud look is not set up" and everything else works offline.
