import asyncio
import logging
import secrets
from collections import deque
from time import monotonic

from fastapi import FastAPI, Header, Depends
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .config import Settings
from .errors import AiError
from .gemini_client import answer
from .schemas import ChatRequest, ChatResponse

settings = Settings()
app = FastAPI(title='Warehouse read-only AI', docs_url=None, redoc_url=None, openapi_url=None)
active = 0
recent: deque[float] = deque()
logging.basicConfig(level=logging.INFO)
# Third-party HTTP logs can include request URLs. Only our sanitized operational metrics are needed.
for name in ('httpx', 'httpcore', 'google_genai'):
    logging.getLogger(name).setLevel(logging.CRITICAL)


@app.exception_handler(AiError)
async def ai_error(_, exc: AiError):
    return JSONResponse(status_code=exc.status, content={'code': exc.code, 'message': exc.message})


@app.exception_handler(RequestValidationError)
async def invalid_request(_, exc):
    # Pydantic errors normally echo inputs; do not echo capability tickets.
    return JSONResponse(status_code=422, content={'code': 'INVALID_REQUEST', 'message': '请求格式无效。'})


@app.exception_handler(Exception)
async def unexpected(_, exc):
    logging.getLogger('warehouse.ai').error('error=UNEXPECTED type=%s', type(exc).__name__)
    return JSONResponse(status_code=503, content={'code': 'AI_UNAVAILABLE', 'message': 'AI 服务暂不可用，请稍后重试。'})


async def authenticate(authorization: str | None = Header(default=None)):
    token = settings.ai_internal_token.get_secret_value()
    if len(token) < 32:
        raise AiError('INTERNAL_CONFIG', 'AI 内部认证未配置，Token 至少需要 32 字符。')
    if not authorization or not secrets.compare_digest(authorization, 'Bearer ' + token):
        raise AiError('FORBIDDEN', '内部认证失败。', 403)


@app.get('/health')
async def health():
    return {'status': 'ok', 'enabled': settings.ai_enabled,
            'ready': settings.ai_enabled and bool(settings.gemini_api_key.get_secret_value()) and len(settings.ai_internal_token.get_secret_value()) >= 32}


@app.post('/chat', response_model=ChatResponse, dependencies=[Depends(authenticate)])
async def chat(request: ChatRequest):
    global active
    now = monotonic()
    while recent and recent[0] < now - 60:
        recent.popleft()
    if active >= 2 or len(recent) >= 10:
        raise AiError('RATE_LIMIT', 'AI 请求较多，请稍后重试。', 429)
    active += 1
    recent.append(now)
    try:
        return await answer(request, settings)
    finally:
        active -= 1
