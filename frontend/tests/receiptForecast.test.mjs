import { test } from 'node:test';
import assert from 'node:assert/strict';
import { packingRemainder, palletSummary, formatExactCount } from '../src/utils/receiptForecast.ts';
import { packingError } from '../src/utils/boxPacking.ts';
test('carton and pallet remainders retain boxes, units and exact fractions',()=>{
  const quantity={cartons:'83',boxes:'2',units:'6',fraction:'2/3',pallets:'2',palletCartons:'3'};
  assert.equal(packingRemainder(quantity,'个'),'2 盒 + 6 个 + 2/3 个');
  assert.equal(palletSummary(quantity,'个'),'2 托盘 + 3 箱 + 2 盒 + 6 个 + 2/3 个');
  assert.equal(palletSummary({...quantity,pallets:null},'个'),'');
  assert.equal(formatExactCount('9007199254740993'),'9,007,199,254,740,993');
});
test('optional pallet presets validate independently of box contents',()=>{
  const packing={boxesPerCarton:22,unitsPerBox:30};
  assert.equal(packingError(packing,'个'),'');
  assert.equal(packingError({...packing,cartonsPerPallet:40},'个'),'');
  assert.equal(packingError({...packing,cartonsPerPallet:''},'个'),'');
  for(const cartonsPerPallet of [0,-1,1.5,2147483648])assert.ok(packingError({...packing,cartonsPerPallet},'个'));
});
