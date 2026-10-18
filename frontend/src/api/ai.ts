import { http } from './http';
import type { WarehouseFilters } from '../utils/warehouse';
export type AiScope = Partial<WarehouseFilters> & { taskId?: number };
export interface Amounts { quantities: Record<string,string>; pending:number; unitUnknown:number; palletIds:number[] }
export interface AiLocation extends Amounts { location:string; pallets:string[]; rawBatches:string[] }
export interface AiBatch extends Amounts { label:string; missing:boolean; locations:AiLocation[] }
export interface AiMaterial extends Amounts { name:string; code:string; productId:number|null; batches:AiBatch[] }
export interface AiTask {
  id:number; name:string; status:string; progressPercent:number;
  targets:{productId:number;productName:string;materialCode:string|null;baseUnit:string;targetQuantity:number;issuedQuantity:number;returnedQuantity:number;completedQuantity:number;shortageQuantity:number;unknownQuantityCount:number}[];
  output?:{productName:string;baseUnit:string;receivedQuantity:number;productionPercent:number|null;lossPercent:number|null;provisional:boolean;unknownQuantityCount:number;producedQuantity:number|null;netIssuedQuantity:number|null}|null;
  movements:{id:number;recordNo:string;status:string;returnMovement:boolean;direction:string;unknownQuantityCount:number}[];
}
export interface AiResult { source:string; queriedAt:string; scope:AiScope; inventory?:{materialCount:number;palletCount:number;pendingPalletCount:number;materials:AiMaterial[];detailsOmitted:boolean}|null; task?:AiTask|null }
export interface AiReply { answer:string; results:AiResult[] }
export async function askAi(message:string,scope:AiScope,signal:AbortSignal):Promise<AiReply> {
  return (await http.post('/ai/chat',{message,scope},{timeout:85000,signal})).data;
}
