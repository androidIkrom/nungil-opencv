#!/usr/bin/env bash
# A moving 640x480 video from the desk photos (slow zoom over each, 25 frames at 10 fps), for CvSoakTest on the
# emulator: app/src/androidTest/assets/videos/desks.mp4 (git-ignored, like the photos). Needs ffmpeg.
set -e
cd "$(dirname "$0")/../.."
mkdir -p app/src/androidTest/assets/videos
list=$(mktemp)
for f in app/src/androidTest/assets/desks/*.jpg; do
  printf "file '%s'\n" "$(cd "$(dirname "$f")" && pwd -W 2>/dev/null || pwd)/$(basename "$f")" >> "$list"
done
ffmpeg -y -loglevel error -f concat -safe 0 -i "$list" \
  -vf "scale=960:720:force_original_aspect_ratio=increase,crop=960:720,zoompan=z='min(zoom+0.0015,1.5)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=25:s=640x480:fps=10,format=yuv420p" \
  -c:v libx264 -preset veryfast -crf 23 app/src/androidTest/assets/videos/desks.mp4
rm -f "$list"
ffprobe -v error -select_streams v -count_frames -show_entries stream=nb_read_frames,width,height -of csv=p=0 app/src/androidTest/assets/videos/desks.mp4
