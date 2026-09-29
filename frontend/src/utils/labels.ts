import type {Direction,IssueType} from '../types';
export const directionLabel=(d:Direction)=>d==='WAREHOUSE_TO_PRODUCTION'?'仓库 → 生产车间':'生产车间 → 仓库';
export const issueLabels:Record<IssueType,string>={MISSING_LABEL:'缺少标签',WRONG_LABEL:'标签错误',DAMAGED_CARTON:'外箱破损',QUANTITY_MISMATCH:'数量不一致',PACKAGING_ISSUE:'包装异常',OTHER:'其他'};
