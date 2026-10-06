const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const cases=JSON.parse(fs.readFileSync('tests/fixtures/loyalty-rewards.json','utf8'));
const source=fs.readFileSync('app/src/main/assets/pos/Web/js/features/loyalty.js','utf8');
test('reviewed allocation and receipt snapshot match shared Kotlin loyalty fixtures',()=>{
 for(const c of cases){const state={cart:c.items,loyaltyPrograms:c.programs,loyaltyRedemptions:c.redemptions},before=JSON.stringify(state);const ctx={state};ctx.window=ctx;vm.createContext(ctx);vm.runInContext(source,ctx);
 const allocation=ctx.loyaltyRewardAllocation();assert.deepEqual(JSON.parse(JSON.stringify({...allocation,snapshot:ctx.loyaltyReceiptSnapshot(allocation)})),c.expected,c.name);
 const valid=Object.entries(c.redemptions).every(([id,value])=>Math.max(0,Math.trunc(Number(value)||0))===(allocation.allocations[id]||[]).reduce((s,row)=>s+Number(row.quantity||0),0));assert.equal(valid,c.valid,c.name);assert.equal(JSON.stringify(state),before);}
});
