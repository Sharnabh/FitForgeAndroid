from enum import Enum
from typing import List, Optional, Any, Dict
from pydantic import BaseModel, Field
import time

class ActionType(str, Enum):
    CREATE_REMINDER = "CREATE_REMINDER"
    REVIEW_WORKOUT = "REVIEW_WORKOUT"
    SCHEDULE_MAKEUP = "SCHEDULE_MAKEUP"
    BLOCK_APP = "BLOCK_APP"
    BLOCK_WEBSITE = "BLOCK_WEBSITE"
    ADJUST_POINTS = "ADJUST_POINTS"

class ActionStatus(str, Enum):
    PENDING = "PENDING"
    CONFIRMED = "CONFIRMED"
    REJECTED = "REJECTED"
    EXPIRED = "EXPIRED"

class AppUsageDto(BaseModel):
    packageName: str
    appName: str
    foregroundMinutes: int

class WebsiteUsageDto(BaseModel):
    domain: str
    foregroundMinutes: int
    visitCount: int

class AccountabilityContextDto(BaseModel):
    localDate: str
    plannedWorkout: Optional[str] = None
    workoutStatus: str = "PENDING"  # "COMPLETED", "PENDING", "MISSED"
    completedSessionsThisWeek: int = 0
    missedSessionsThisWeek: int = 0
    streakDays: int = 0
    topDistractingApps: List[AppUsageDto] = Field(default_factory=list)
    topDistractingWebsites: List[WebsiteUsageDto] = Field(default_factory=list)

class ChatMessage(BaseModel):
    role: str  # "user", "assistant", "system"
    content: str

class ChatRequest(BaseModel):
    message: str
    context: AccountabilityContextDto
    history: List[ChatMessage] = Field(default_factory=list)

class ActionProposal(BaseModel):
    id: str
    type: ActionType
    title: str
    reason: str
    payload: Dict[str, Any]
    status: ActionStatus = ActionStatus.PENDING
    createdAt: int = Field(default_factory=lambda: int(time.time()))
    expiresAt: int = Field(default_factory=lambda: int(time.time() + 3600))  # 1 hour default

class PairStartResponse(BaseModel):
    code: str
    expiresInSeconds: int

class PairCompleteRequest(BaseModel):
    code: str
    deviceName: str

class PairCompleteResponse(BaseModel):
    token: str
    tokenType: str = "Bearer"
