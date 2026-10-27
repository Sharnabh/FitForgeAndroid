import json
import os
import re
import secrets
import string
import time
import uuid
from typing import AsyncGenerator

from fastapi import Depends, FastAPI, HTTPException, Header, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
import httpx

import database
from models import (
    ActionProposal,
    ActionStatus,
    ActionType,
    ChatRequest,
    PairCompleteRequest,
    PairCompleteResponse,
    PairStartResponse,
)
from prompts import ISHITA_SYSTEM_PROMPT, format_context_prompt

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "R4C3R/qwen3-8b-heretic")

app = FastAPI(title="FitForge Ishita Local Accountability Bridge", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
def on_startup():
    database.init_db()

# -------------------------------------------------------------
# AUTHENTICATION
# -------------------------------------------------------------
async def verify_device_token(authorization: str = Header(None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid Bearer token"
        )
    token = authorization[7:].strip()
    if not database.is_token_valid(token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Device token is invalid or has been revoked"
        )
    return token

# -------------------------------------------------------------
# PAIRING ENDPOINTS
# -------------------------------------------------------------
@app.post("/v1/pair/start", response_model=PairStartResponse)
def start_pairing():
    """Generates a 6-character single-use code to pair the Android app."""
    chars = string.ascii_uppercase + string.digits
    code = "".join(secrets.choice(chars) for _ in range(6))
    database.create_pairing_code(code, ttl_seconds=300)
    return PairStartResponse(code=code, expiresInSeconds=300)

@app.post("/v1/pair/complete", response_model=PairCompleteResponse)
def complete_pairing(req: PairCompleteRequest):
    """Exchanges a valid pairing code for a persistent device bearer token."""
    code = req.code.strip().upper()
    if not database.validate_and_consume_code(code):
        raise HTTPException(status_code=400, detail="Invalid or expired pairing code")

    token = secrets.token_hex(32)
    database.store_device_token(token, req.deviceName)
    return PairCompleteResponse(token=token)

# -------------------------------------------------------------
# HEALTH & STATUS
# -------------------------------------------------------------
@app.get("/v1/health")
async def health_check():
    ollama_ok = False
    models = []
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.get(f"{OLLAMA_BASE_URL}/api/tags")
            if resp.status_code == 200:
                ollama_ok = True
                data = resp.json()
                models = [m.get("name") for m in data.get("models", [])]
    except Exception:
        pass

    return {
        "status": "healthy" if ollama_ok else "degraded",
        "ollamaConnected": ollama_ok,
        "activeModel": OLLAMA_MODEL,
        "availableModels": models
    }

# -------------------------------------------------------------
# CHAT & PROPOSALS STREAMING
# -------------------------------------------------------------
PROPOSAL_REGEX = re.compile(r'<proposal\s+([^>]+)/>', re.IGNORECASE)
ATTR_REGEX = re.compile(r'(\w+)="([^"]*)"')

def parse_proposals_from_text(full_text: str):
    proposals = []
    for match in PROPOSAL_REGEX.finditer(full_text):
        raw_attrs = match.group(1)
        attrs = dict(ATTR_REGEX.findall(raw_attrs))
        p_type = attrs.get("type", "").upper()

        if p_type == "BLOCK_APP":
            proposals.append(ActionProposal(
                id=str(uuid.uuid4()),
                type=ActionType.BLOCK_APP,
                title=f"Block {attrs.get('name', 'App')}",
                reason=attrs.get("reason", "Accountability penalty for missed session"),
                payload={
                    "packageName": attrs.get("package", ""),
                    "appName": attrs.get("name", ""),
                    "durationMinutes": int(attrs.get("duration", "60"))
                }
            ))
        elif p_type == "BLOCK_WEBSITE":
            proposals.append(ActionProposal(
                id=str(uuid.uuid4()),
                type=ActionType.BLOCK_WEBSITE,
                title=f"Block {attrs.get('domain', 'Website')}",
                reason=attrs.get("reason", "Accountability restriction on distracting website"),
                payload={
                    "domain": attrs.get("domain", ""),
                    "durationMinutes": int(attrs.get("duration", "60"))
                }
            ))
        elif p_type == "SCHEDULE_MAKEUP":
            proposals.append(ActionProposal(
                id=str(uuid.uuid4()),
                type=ActionType.SCHEDULE_MAKEUP,
                title=f"Schedule {attrs.get('routine', 'Makeup Session')}",
                reason=attrs.get("reason", "Makeup session for skipped training"),
                payload={
                    "dayOffset": int(attrs.get("day_offset", "1")),
                    "routineName": attrs.get("routine", "Makeup Routine")
                }
            ))
        elif p_type == "CREATE_REMINDER":
            proposals.append(ActionProposal(
                id=str(uuid.uuid4()),
                type=ActionType.CREATE_REMINDER,
                title="Accountability Reminder",
                reason=attrs.get("reason", "Follow-through reminder"),
                payload={
                    "message": attrs.get("message", "Time to train")
                }
            ))
    return proposals

async def stream_ollama_response(req: ChatRequest) -> AsyncGenerator[str, None]:
    system_instruction = ISHITA_SYSTEM_PROMPT + "\n\n" + format_context_prompt(req.context)

    messages = [{"role": "system", "content": system_instruction}]
    for h in req.history:
        messages.append({"role": h.role, "content": h.content})
    messages.append({"role": "user", "content": req.message})

    payload = {
        "model": OLLAMA_MODEL,
        "messages": messages,
        "stream": True
    }

    full_response_text = ""

    try:
        async with httpx.AsyncClient(timeout=120.0) as client:
            async with client.stream("POST", f"{OLLAMA_BASE_URL}/api/chat", json=payload) as response:
                if response.status_code != 200:
                    yield f"event: error\ndata: {json.dumps({'error': 'Ollama error: ' + str(response.status_code)})}\n\n"
                    return

                async for line in response.aiter_lines():
                    if not line:
                        continue
                    try:
                        chunk = json.loads(line)
                        content = chunk.get("message", {}).get("content", "")
                        if content:
                            full_response_text += content
                            # Send SSE text delta
                            yield f"event: text\ndata: {json.dumps({'delta': content})}\n\n"
                        if chunk.get("done", False):
                            break
                    except Exception:
                        pass
    except Exception as e:
        yield f"event: error\ndata: {json.dumps({'error': str(e)})}\n\n"
        return

    # Post-process full response for proposals
    detected_proposals = parse_proposals_from_text(full_response_text)
    for p in detected_proposals:
        database.save_proposal(p)
        yield f"event: proposal\ndata: {json.dumps(p.dict())}\n\n"

    yield "event: done\ndata: {}\n\n"

@app.post("/v1/chat")
async def chat_with_coach(req: ChatRequest, token: str = Depends(verify_device_token)):
    """Streams Ishita's response as Server-Sent Events (SSE)."""
    return StreamingResponse(
        stream_ollama_response(req),
        media_type="text/event-stream"
    )

# -------------------------------------------------------------
# ACTION PROPOSAL CONFIRMATION & AUDIT
# -------------------------------------------------------------
@app.get("/v1/actions/pending")
def list_pending_actions(token: str = Depends(verify_device_token)):
    proposals = database.get_pending_proposals()
    return {"proposals": [p.dict() for p in proposals]}

@app.post("/v1/actions/{action_id}/confirm")
def confirm_action(action_id: str, token: str = Depends(verify_device_token)):
    success = database.update_proposal_status(action_id, ActionStatus.CONFIRMED)
    if not success:
        raise HTTPException(status_code=404, detail="Proposal not found or already handled")
    return {"status": "confirmed", "actionId": action_id}

@app.post("/v1/actions/{action_id}/reject")
def reject_action(action_id: str, token: str = Depends(verify_device_token)):
    success = database.update_proposal_status(action_id, ActionStatus.REJECTED)
    if not success:
        raise HTTPException(status_code=404, detail="Proposal not found or already handled")
    return {"status": "rejected", "actionId": action_id}
