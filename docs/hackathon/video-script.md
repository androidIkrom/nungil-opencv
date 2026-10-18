# Video script (at most five minutes)

Record on the phone with the screen captured (scrcpy or the phone's recorder) and the room audio on, so the
judges hear what Nungil says. Trim with `ffmpeg -ss <start> -to <end> -i raw.mp4 -c copy nungil.mp4`. Upload
unlisted. Every shot says what is on screen and what is spoken.

| Time | On screen | Said (by the narrator, N, or by Nungil, Ng) |
|---|---|---|
| 0:00-0:30 | Title card: "Nungil: an on-device scene assistant for blind users". Then a hand holding the phone upright in a room. | N: "A blind user holds the phone and turns around. Nungil says what is there, where it is and what colour it is, in English or Korean, with no internet. For the OpenCV AI Competition we moved three steps of the pipeline to OpenCV 5 and added one online ability on AWS." |
| 0:30-1:15 | Live scan over a desk with a blue chair, a laptop and a bottle. The caption bar shows the sentences. | Ng: "A blue chair in front; a black laptop on your right; a white bottle." N: "The colours come from OpenCV 5: the whole object region is white-balanced and counted in HSV bands with inRange. Before, 576 sampled points; now every pixel, with the same names on 99 % of regions." |
| 1:15-2:00 | Saved objects: "Eye, add object charger". The outline is drawn around the charger while the phone moves; a speck on the table is not in it. | Ng: "Hold it still… saved: charger." N: "The segmenter's mask is cleaned by OpenCV: an opening, a closing and the connected component under the finger. Thirty test masks went from 7.7 pieces each to one, with the item kept at 97 % overlap." |
| 2:00-2:45 | Walk mode in a corridor with a step. Depth overlay visible in the debug build. | Ng: "Wall ahead, 3 steps." … "Step up, 2 steps." N: "ARCore depth passes through an OpenCV median before Nungil reads floors and walls: spikes go, edges stay, and a pixel it could not measure stays unmeasured." |
| 2:45-3:45 | "Eye, look closer" at a sign on a wall, online. Then airplane mode, and again. | Ng: "Looking closer." … (the cloud's two sentences, reading the sign). N: "One frame goes to a Lambda on Graviton: OpenCV 5 crops, sizes, lifts a dark frame and reads any QR code; Amazon Bedrock answers in two sentences, in the user's language." Then: Ng: "No internet. Nungil keeps working offline." N: "Nothing else depends on the network." |
| 3:45-4:15 | Browser: the judges' page. Upload a photo, ask in Korean, read the answer and the time. | N: "The same Lambda serves this page for the judges. Nothing is stored." |
| 4:15-4:45 | Architecture diagram, then the evaluation tables (colour, mask, depth, detectors, faces) and the decision-trace plot. | N: "Every step of Find and Walk is traced: what was seen, what was decided, what the user got. The report has the measurements." |
| 4:45-5:00 | Repo and licence card. | N: "Nungil is open source under AGPL-3.0. Thank you." |

Checklist before recording: phone volume up; Korean TTS installed; `local.properties` has the cloud URL and key;
airplane mode toggle ready; the debug build (depth overlay) for the Walk shot, the release build elsewhere.
