# FitForge Ishita Bridge (Mac Host)

This lightweight FastAPI service connects your local **Ollama** LLM on macOS with the **FitForge Android** client via private HTTPS over **Tailscale**.

---

## 1. Prerequisites

### Install Ollama & Pull Model
```bash
# In your Mac Terminal:
brew install ollama  # or download from https://ollama.com
ollama serve         # Start local Ollama daemon

# Pull the Ishita persona model:
ollama pull R4C3R/qwen3-8b-heretic
```

---

## 2. Setup & Run the Bridge

```bash
cd bridge

# Create and activate virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Start the bridge server
uvicorn server:app --host 0.0.0.0 --port 8765
```

---

## 3. Expose via Tailscale Serve (HTTPS)

```bash
# In another terminal window:
tailscale serve https / http://127.0.0.1:8765
```
Your Mac's Tailscale HTTPS address (e.g. `https://your-mac.tailscale.net`) can now be entered directly into FitForge Android.

---

## 4. API Endpoints

* `GET /v1/health` - Check bridge and Ollama connectivity
* `POST /v1/pair/start` - Generate 5-minute single-use pairing code
* `POST /v1/pair/complete` - Exchange pairing code for persistent bearer token
* `POST /v1/chat` - Stream SSE responses and proposal cards from Ollama
* `GET /v1/actions/pending` - List pending proposal cards
* `POST /v1/actions/{id}/confirm` - Confirm a proposal
* `POST /v1/actions/{id}/reject` - Reject a proposal
