import httpx
from pydantic import ValidationError

from .config import Settings
from .errors import AiError
from .schemas import ToolFilters, ToolResult

DESCRIPTIONS = {
    'search_products': '查找当前已录入托盘台账中的物料身份，精确区分同名不同编码。',
    'get_warehouse_material_summary': '完整范围物料、批次、各单位确认数量和去重托盘数量；库存只能查询台账。',
    'get_batch_locations': '查询物料批号对应的真实货位，batch 是物料批号，不是原料批号。',
    'get_pending_inventory': '查询数量为空的待盘点物料及托盘。',
    'get_production_task_details': '查询当前页面任务的权威备料和生产完成度。不能指定其他任务 ID。',
    'get_movement_summary': '查询当前任务关联流转记录及物料明细，作废记录不计入统计。',
}


async def execute(settings: Settings, ticket: str, name: str, arguments: dict) -> ToolResult:
    if name not in DESCRIPTIONS:
        raise AiError('INVALID_TOOL', '模型请求了不支持的工具，请重新提问。', 422)
    try:
        filters = ToolFilters.model_validate(arguments)
    except ValidationError:
        raise AiError('INVALID_ARGUMENTS', '查询条件无效，请明确物料、批号或位置。', 422) from None
    # Only this fixed endpoint can be called. Credentials and ticket never enter model contents.
    async with httpx.AsyncClient(timeout=10, follow_redirects=False, trust_env=False) as client:
        try:
            response = await client.post(
                settings.spring_boot_internal_url.rstrip('/') + '/internal/ai/tools',
                headers={'Authorization': 'Bearer ' + settings.ai_internal_token.get_secret_value()},
                json={'ticket': ticket, 'name': name, 'filters': filters.model_dump()},
            )
            if response.status_code == 400:
                raise AiError('TOOL_SCOPE', '范围不适用或结果过多，请在对应页面缩小范围后重试。', 422)
            if response.status_code in (401, 403):
                raise AiError('TOOL_AUTH', '内部认证或查询范围已失效，请重新登录或检查内部 Token。', 403)
            if response.status_code == 404:
                raise AiError('NOT_FOUND', '对应业务记录已不存在，请刷新页面。', 404)
            response.raise_for_status()
            if len(response.content) > 200000:
                raise AiError('TOOL_SIZE', '结果过多，请缩小范围。', 422)
            return ToolResult.model_validate(response.json())
        except httpx.TimeoutException:
            raise AiError('TOOL_TIMEOUT', '业务查询超时，请稍后重试。', 504) from None
        except (httpx.HTTPError, ValidationError, ValueError):
            raise AiError('TOOL_UNAVAILABLE', '业务数据查询失败，不能将失败视作零库存。') from None
