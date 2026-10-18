# Nungil cloud: "look closer"

The one AWS component of Nungil. The phone stays fully offline for everything else; when the user says
"Eye, look closer" (Korean "눈길아 자세히 봐") and the phone is online, the last camera frame goes to this Lambda
function, which runs on AWS Graviton (arm64):

1. `app/prep.py`, OpenCV 5: crop to the thing the user pointed at (when Find has a box), shrink to a 1280 px long
   side, lift a dark frame with CLAHE, read any QR code.
2. `app/describe.py`, Amazon Bedrock (Converse API, Nova Lite by default): two sentences for a blind user, in the
   user's language, text read verbatim, no guesses about who a person is.
3. `app/handler.py`: `POST /look` for the phone and `GET /` for the judges' page (`web/index.html`).

Nothing is stored: no image, no user id. One log line per request carries status, time and size.

## Run the tests

```bash
python -m venv cloud/.venv
cloud/.venv/Scripts/python -m pip install -r cloud/requirements-dev.txt
cloud/.venv/Scripts/python -m pytest cloud/tests -q
```

## Deploy

`deploy.sh` (added with the deployment task) needs AWS CLI v2, `aws configure` for `us-east-1`, and Bedrock model
access for Nova Lite. It builds the OpenCV layer for `manylinux2014_aarch64`, creates the role, the function
(arm64, Python 3.12, 1024 MB, 30 s) and a Function URL, and prints the URL. The shared key goes in the function's
`NUNGIL_KEY` environment variable and in the phone's `local.properties` as `NUNGIL_CLOUD_KEY`; it is never
committed.
