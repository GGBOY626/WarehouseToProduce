import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest
from fastapi.testclient import TestClient
from google.genai import types, errors
from google import genai
from pydantic import ValidationError

from app.config import Settings
from app.schemas import ChatRequest, ToolFilters, ToolResult
from app.errors import AiError
from app.gemini_client import answer
from app.tools import execute
from app import main

TOKEN = 'test-only-internal-token-longer-than-32'


def settings(**kwargs):
    return Settings(_env_file=None, gemini_api_key='test-key-not-real', ai_internal_token=TOKEN, **kwargs)


def request(**scope):
    return ChatRequest(message='A 排有哪些物料？', scope=scope, ticket='12345678-1234-1234-1234-123456789012')


def result():
    return ToolResult(source='当前已录入托盘台账', queriedAt='2026-10-18T10:00:00+13:00', scope={},
                      inventory={'materialCount': 0, 'palletCount': 0, 'pendingPalletCount': 0, 'materials': []})


def response(parts):
    return types.GenerateContentResponse(candidates=[types.Candidate(content=types.Content(role='model', parts=parts))], usage_metadata=types.GenerateContentResponseUsageMetadata(total_token_count=50))


def call(name='get_warehouse_material_summary', args=None):
    return response([types.Part(function_call=types.FunctionCall(name=name, args=args or {}, id='call-1'), thought_signature=b'test-signature')])


def final(text='{"evidence_ids":[0]}'):
    return response([types.Part.from_text(text=text)])


def client(*responses):
    generate = AsyncMock(side_effect=list(responses))
    return SimpleNamespace(aio=SimpleNamespace(models=SimpleNamespace(generate_content=generate))), generate


def test_dotenv_environment_priority_and_secret_repr(tmp_path, monkeypatch):
    path = tmp_path / '.env'
    path.write_text('GEMINI_API_KEY=dotenv-secret\nGEMINI_MODEL=local-model\nAI_ENABLED=false\n', encoding='utf-8')
    monkeypatch.setenv('GEMINI_MODEL', 'environment-model')
    s = Settings(_env_file=path)
    assert s.gemini_model == 'environment-model'
    assert s.gemini_api_key.get_secret_value() == 'dotenv-secret'
    assert not s.ai_enabled
    assert 'dotenv-secret' not in repr(s)


@pytest.mark.parametrize('args', [{'url':'https://example.com'}, {'query':'x'*201}, {'productId':-1}, {'productId':'1'}, {'row':'Z'}, {'unknownOnly':'true'}, {'taskId':42}])
def test_illegal_tool_parameters(args):
    with pytest.raises(ValidationError):
        ToolFilters.model_validate(args)


@pytest.mark.asyncio
async def test_function_cycle_preserves_signatures_and_never_displays_hallucinated_numbers():
    c, generate = client(call(), final('库存有 999999 件 <script>evil()</script>'))
    executor = AsyncMock(return_value=result())
    reply = await answer(request(row='A'), settings(), c, executor)
    assert '999999' not in reply.answer and '<script>' not in reply.answer
    assert '0 种物料' in reply.answer
    executor.assert_awaited_once()
    args = generate.call_args.kwargs
    assert args['model'] == 'gemini-3.1-flash-lite'
    assert args['contents'][1].parts[0].thought_signature == b'test-signature'
    assert args['contents'][2].parts[0].function_response.id == 'call-1'
    assert request().ticket not in str(args['contents'])
    assert TOKEN not in str(args['contents'])
    assert args['config'].automatic_function_calling.disable
    assert all(not t.google_search and not t.code_execution for t in args['config'].tools)


@pytest.mark.asyncio
async def test_no_results_and_no_tool_response_are_distinct():
    c, _ = client(final())
    reply = await answer(request(), settings(), c)
    assert not reply.results and '请明确' in reply.answer
    c, _ = client(call(), final())
    reply = await answer(request(), settings(), c, AsyncMock(return_value=result()))
    assert reply.results[0].inventory.materialCount == 0


@pytest.mark.asyncio
async def test_missing_key_and_disabled_never_call_gemini():
    c, generate = client()
    s = Settings(_env_file=None)
    with pytest.raises(AiError, match='GEMINI_KEY_MISSING'):
        await answer(request(), s, c)
    with pytest.raises(AiError, match='AI_DISABLED'):
        await answer(request(), settings(ai_enabled=False), c)
    generate.assert_not_called()


@pytest.mark.parametrize('error,code', [
    (errors.ClientError(429, {'error':{'message':'secret upstream message'}}), 'GEMINI_QUOTA'),
    (errors.ClientError(403, {'error':{'message':'secret upstream message'}}), 'GEMINI_PERMISSION'),
    (httpx.ReadTimeout('secret'), 'GEMINI_TIMEOUT'),
    (httpx.ConnectError('secret'), 'GEMINI_NETWORK'),
])
@pytest.mark.asyncio
async def test_provider_failures_are_sanitized(error,code):
    c, generate = client(error)
    with pytest.raises(AiError, match=code) as exc:
        await answer(request(), settings(), c)
    assert 'secret' not in exc.value.message
    assert generate.await_count == 1


@pytest.mark.asyncio
async def test_only_transient_server_error_retries_once():
    c, generate = client(errors.ServerError(503, {}), final())
    await answer(request(), settings(), c)
    assert generate.await_count == 2


@pytest.mark.asyncio
async def test_tool_call_and_round_limits():
    c, _ = client(call(), call())
    with pytest.raises(AiError, match='TOOL_LIMIT'):
        await answer(request(), settings(max_tool_calls=1), c, AsyncMock(return_value=result()))
    c, _ = client(call())
    with pytest.raises(AiError, match='ROUND_LIMIT'):
        await answer(request(), settings(max_rounds=1), c, AsyncMock(return_value=result()))


@pytest.mark.asyncio
async def test_model_cannot_use_task_tool_in_stock_scope():
    c, _ = client(call('get_production_task_details'))
    executor = AsyncMock()
    with pytest.raises(AiError, match='TOOL_SCOPE'):
        await answer(request(), settings(), c, executor)
    executor.assert_not_called()


@pytest.mark.asyncio
async def test_fixed_http_endpoint_and_schema_validation(monkeypatch):
    async def post(self, url, **kwargs):
        assert url == 'http://127.0.0.1:8080/internal/ai/tools'
        assert kwargs['headers']['Authorization'] == 'Bearer ' + TOKEN
        assert kwargs['json']['filters']['row'] == 'A'
        return httpx.Response(200, json=result().model_dump(), request=httpx.Request('POST',url))
    monkeypatch.setattr(httpx.AsyncClient, 'post', post)
    r = await execute(settings(), request().ticket, 'get_batch_locations', {'row':'A'})
    assert r.inventory.palletCount == 0
    with pytest.raises(AiError, match='INVALID_TOOL'):
        await execute(settings(), request().ticket, 'execute_sql', {})
    with pytest.raises(AiError, match='INVALID_ARGUMENTS'):
        await execute(settings(), request().ticket, 'get_batch_locations', {'url':'https://evil'})


@pytest.mark.parametrize('status,code', [(403,'TOOL_AUTH'),(400,'TOOL_SCOPE'),(500,'TOOL_UNAVAILABLE'),(404,'NOT_FOUND')])
@pytest.mark.asyncio
async def test_business_errors_not_empty_inventory(monkeypatch,status,code):
    async def post(self,url,**kwargs):
        return httpx.Response(status, json={}, request=httpx.Request('POST',url))
    monkeypatch.setattr(httpx.AsyncClient, 'post', post)
    with pytest.raises(AiError, match=code):
        await execute(settings(), request().ticket, 'get_warehouse_material_summary', {})


def test_fastapi_auth_health_configuration_and_validation(monkeypatch):
    monkeypatch.setattr(main, 'settings', settings())
    main.recent.clear()
    with TestClient(main.app) as api:
        assert api.get('/health').json()['ready']
        assert api.post('/chat', json=request().model_dump()).status_code == 403
        invalid = api.post('/chat', headers={'Authorization':'Bearer '+TOKEN}, json={'ticket':'private-ticket'})
        assert invalid.status_code == 422 and 'private-ticket' not in invalid.text
        monkeypatch.setattr(main, 'settings', Settings(_env_file=None, ai_internal_token=TOKEN))
        reply = api.post('/chat', headers={'Authorization':'Bearer '+TOKEN}, json=request().model_dump())
        assert reply.status_code == 503 and reply.json()['code'] == 'GEMINI_KEY_MISSING'
        assert api.get('/health').status_code == 200


def test_fastapi_concurrency_limit(monkeypatch):
    monkeypatch.setattr(main, 'settings', settings())
    monkeypatch.setattr(main, 'active', 2)
    with TestClient(main.app) as api:
        assert api.post('/chat',headers={'Authorization':'Bearer '+TOKEN},json=request().model_dump()).status_code == 429


@pytest.mark.asyncio
async def test_request_timeout(monkeypatch):
    import asyncio
    c, generate = client()
    async def slow(**kwargs):
        await asyncio.sleep(5)
    generate.side_effect = slow
    with pytest.raises(AiError, match='REQUEST_TIMEOUT'):
        await answer(request(), settings(request_timeout=1), c)


@pytest.mark.asyncio
async def test_real_sdk_serializes_manual_function_cycle_over_mock_http():
    sent = []
    def handle(req):
        body = json.loads(req.content)
        sent.append(body)
        assert 'test-key-not-real' not in req.url.query.decode()
        if len(sent) == 1:
            parts = [{'functionCall':{'name':'get_warehouse_material_summary','args':{'row':'A'},'id':'wire-id'}}]
        else:
            assert body['contents'][-1]['role'] == 'user'
            assert body['contents'][-1]['parts'][0]['functionResponse']['id'] == 'wire-id'
            parts = [{'text':'{"evidence_ids":[0]}'}]
        return httpx.Response(200,json={'candidates':[{'content':{'role':'model','parts':parts}}],'usageMetadata':{'totalTokenCount':20}})
    sdk = genai.Client(api_key='test-key-not-real',http_options=types.HttpOptions(async_client_args={'transport':httpx.MockTransport(handle)}))
    try:
        reply = await answer(request(row='A'),settings(),sdk,AsyncMock(return_value=result()))
        assert reply.results[0].inventory.materialCount == 0
        assert len(sent) == 2
    finally:
        await sdk.aio.aclose()
        sdk.close()


def test_task_explanations_distinguish_shortage_void_unknown_and_provisional():
    from app.evidence import statements
    data = result().model_dump()
    data['inventory'] = None
    data['task'] = {
        'id':1,'name':'任务','batchNo':'B1','status':'IN_PROGRESS','progressPercent':50,
        'targets':[{'productId':1,'productName':'主料','baseUnit':'件','targetQuantity':20000,'completedQuantity':10000,'issuedQuantity':12000,'returnedQuantity':2000,'shortageQuantity':10000,'unknownQuantityCount':1,'progressPercent':50}],
        'output':{'productName':'成品','baseUnit':'盒','receivedQuantity':300,'unknownQuantityCount':0,'quantityBasis':'BOX','netIssuedQuantity':10000,'producedQuantity':9000,'productionPercent':None,'lossPercent':None,'provisional':True},
        'movements':[{'id':5,'recordNo':'TR-5','direction':'WAREHOUSE_TO_PRODUCTION','returnMovement':False,'status':'VOID','unknownQuantityCount':1,'movementTime':'2026-10-18T00:00:00Z'}],
    }
    text = '\n'.join(statements([ToolResult.model_validate(data)]))
    for phrase in ('不表示全仓无库存','作废记录','未知数量不是零','不是最终实际损耗','暂无法可靠计算'):
        assert phrase in text


@pytest.mark.asyncio
async def test_parallel_calls_count_before_execution_and_input_context_limit():
    many = call()
    many.candidates[0].content.parts *= 7
    c, _ = client(many)
    executor = AsyncMock(return_value=result())
    with pytest.raises(AiError,match='TOOL_LIMIT'):
        await answer(request(),settings(),c,executor)
    executor.assert_not_called()


def test_large_scope_omitted_details_are_not_an_empty_ledger():
    from app.evidence import statements
    r = result()
    r.inventory.materialCount = 100
    r.inventory.detailsOmitted = True
    text = '\n'.join(statements([r]))
    assert '100 种物料' in text and '明细过多' in text
