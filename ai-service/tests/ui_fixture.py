"""Isolated UI smoke fixture: no database or Gemini. Run from repo root on port 5189."""
from pathlib import Path
from fastapi import FastAPI
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

app = FastAPI()
dist = Path(__file__).resolve().parents[2] / 'frontend' / 'dist'
areas = [dict(id=1,name='A',kind='RACK',lanes=3,version=0,rackRow=None)]
item = dict(productId=1,productName='关节饮',materialCode='M1',batch='22344',unit='件',quantity=12000,cartons=None,rawMaterialBatch='RAW-1',photoId=None,photoUrl=None)
pallet = dict(id=1,code='TP-001',areaId=1,areaName='A',kind='RACK',lane='UPPER',position=1,location='A 排 / 上层 / 01 号',notes=None,items=[item],version=0,createdAt='2026-10-18T00:00:00Z',updatedAt='2026-10-18T00:00:00Z')
amounts = dict(quantities={'件':'12000'},pending=0,unitUnknown=0,palletIds=[1])
location = dict(**amounts,location=pallet['location'],areaId=1,lane='UPPER',position=1,pallets=['TP-001'],rawBatches=['RAW-1'])
batch = dict(**amounts,label='22344',missing=False,locations=[location])
material = dict(**amounts,name='关节饮',code='M1',productId=1,batches=[batch])

@app.get('/api/auth/status')
def auth(): return dict(authenticated=True,username='UI fixture')

@app.get('/api/warehouse')
def warehouse(): return dict(areas=areas,pallets=[pallet])

@app.get('/api/products')
def products(): return []

@app.post('/api/ai/chat')
def chat(body:dict):
    if '失败' in body['message']:
        return JSONResponse(status_code=503,content=dict(code='AI_UNAVAILABLE',message='AI 服务暂不可用，普通仓库功能仍可使用。'))
    return dict(answer='当前已录入托盘台账：查询范围内有 1 种物料、1 托盘。',results=[dict(source='当前已录入托盘台账',queriedAt='2026-10-18T13:00:00+13:00[Pacific/Auckland]',scope=body['scope'],inventory=dict(materialCount=1,palletCount=1,pendingPalletCount=0,materials=[material],detailsOmitted=False))])

app.mount('/assets',StaticFiles(directory=dist/'assets'),name='assets')
@app.get('/{path:path}')
def index(path:str): return FileResponse(dist/'index.html')
