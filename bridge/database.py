import sqlite3
import hashlib
import json
import time
import uuid
from typing import List, Optional
from models import ActionProposal, ActionStatus, ActionType

DB_PATH = "fitforge_bridge.db"

def get_connection():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    with get_connection() as conn:
        cursor = conn.cursor()
        # Pairing codes table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS pairing_codes (
                code TEXT PRIMARY KEY,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                used INTEGER NOT NULL DEFAULT 0
            )
        """)
        # Device tokens table (stores sha256 hash of tokens)
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS device_tokens (
                token_hash TEXT PRIMARY KEY,
                device_name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                revoked INTEGER NOT NULL DEFAULT 0
            )
        """)
        # Action proposals table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS action_proposals (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                reason TEXT NOT NULL,
                payload TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            )
        """)
        conn.commit()

def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()

def create_pairing_code(code: str, ttl_seconds: int = 300):
    now = int(time.time())
    with get_connection() as conn:
        conn.execute(
            "INSERT INTO pairing_codes (code, created_at, expires_at, used) VALUES (?, ?, ?, 0)",
            (code, now, now + ttl_seconds)
        )
        conn.commit()

def validate_and_consume_code(code: str) -> bool:
    now = int(time.time())
    with get_connection() as conn:
        cursor = conn.execute(
            "SELECT * FROM pairing_codes WHERE code = ? AND used = 0 AND expires_at > ?",
            (code, now)
        )
        row = cursor.fetchone()
        if not row:
            return False
        conn.execute("UPDATE pairing_codes SET used = 1 WHERE code = ?", (code,))
        conn.commit()
        return True

def store_device_token(token: str, device_name: str):
    token_hash = hash_token(token)
    now = int(time.time())
    with get_connection() as conn:
        conn.execute(
            "INSERT INTO device_tokens (token_hash, device_name, created_at, revoked) VALUES (?, ?, ?, 0)",
            (token_hash, device_name, now)
        )
        conn.commit()

def is_token_valid(token: str) -> bool:
    token_hash = hash_token(token)
    with get_connection() as conn:
        cursor = conn.execute(
            "SELECT revoked FROM device_tokens WHERE token_hash = ?",
            (token_hash,)
        )
        row = cursor.fetchone()
        return row is not None and row["revoked"] == 0

def save_proposal(proposal: ActionProposal):
    with get_connection() as conn:
        conn.execute("""
            INSERT OR REPLACE INTO action_proposals (id, type, title, reason, payload, status, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            proposal.id,
            proposal.type.value,
            proposal.title,
            proposal.reason,
            json.dumps(proposal.payload),
            proposal.status.value,
            proposal.createdAt,
            proposal.expiresAt
        ))
        conn.commit()

def get_pending_proposals() -> List[ActionProposal]:
    now = int(time.time())
    with get_connection() as conn:
        cursor = conn.execute("""
            SELECT * FROM action_proposals 
            WHERE status = 'PENDING' AND expires_at > ? 
            ORDER BY created_at DESC
        """, (now,))
        rows = cursor.fetchall()
        results = []
        for r in rows:
            results.append(ActionProposal(
                id=r["id"],
                type=ActionType(r["type"]),
                title=r["title"],
                reason=r["reason"],
                payload=json.loads(r["payload"]),
                status=ActionStatus(r["status"]),
                createdAt=r["created_at"],
                expiresAt=r["expires_at"]
            ))
        return results

def update_proposal_status(proposal_id: str, new_status: ActionStatus) -> bool:
    with get_connection() as conn:
        cursor = conn.execute(
            "UPDATE action_proposals SET status = ? WHERE id = ?",
            (new_status.value, proposal_id)
        )
        conn.commit()
        return cursor.rowcount > 0
