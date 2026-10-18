# Evaluation evidence

Measurements for the OpenCV AI Competition 2026 entry. Emulator numbers are the Android emulator (x86_64, no
GPU) on the development laptop and are slower than the phone; phone numbers name the phone.

## Colour (OpenCV over the whole region against the 24 x 24 grid sampler)

`CvColorParityTest`, 2026-10-18, emulator x86_64, 42 desk photos (`app/src/androidTest/assets/desks`), five fixed
boxes each (centre 50 % and the four quadrants' centre 50 %).

| | Grid sampler (old) | OpenCV 5 (new) |
|---|---|---|
| Pixels judged per box | 576 | every pixel of the box's centre half |
| Named the same colour | 207 of 210 boxes (99 %) | |
| Time per photo, light + 5 boxes | 6.0 ms | 7.6 ms |

The three disagreements were a red/brown split on a dark table edge and an orange/gray split on a mixed region:
cases where the 576 sampled points under-represented the region. Frame gains agreed within 0.05 on every photo
and darkness on every photo.
