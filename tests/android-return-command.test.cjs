const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/assets/pos'),source=name=>fs.readFileSync(path.join(root,name),'utf8');
function host({legacyReceipt=false}={}){
 const events=[],commands=[],timers=new Map(),initialized=new Set();let timerId=0;
 const receipt={id:'r1',shiftId:'old',total:20,method:'split',payments:[{method:'cash',amount:8},{method:'card',amount:12}],items:[{productId:'p1',qty:1}],customer:{id:'c1'}};
 if(!legacyReceipt)receipt.stockConsumption={version:1,items:[{productId:'p1',qty:0.1}]};
 const state={products:[{id:'p1',type:'simple',stock:0.2}],orders:[...Array.from({length:500},(_,i)=>({id:'other'+i,shiftId:'old',total:0})),receipt],shifts:[{id:'old',status:'closed',openingCash:100,cashMovements:[]},{id:'new',status:'open',openingCash:100,cashMovements:[]}]};
 const initial=structuredClone(state),legacy=new Map(Object.entries(state));
 const context={state,criticalOperationBusy:false,criticalStorageRecoveryPending:false,
  PrilavokCore:{Storage:{async get(key,fallback){return legacy.has(key)?structuredClone(legacy.get(key)):fallback},async set(key,v){legacy.set(key,v)},remove:key=>legacy.delete(key),describe:()=>({})}},
  commitCriticalStorage:async()=>events.push('legacy-commit'),setTimeout(fn,delay){timers.set(++timerId,{fn,delay});return timerId},clearTimeout:id=>timers.delete(id),
  console:{error(){}},uid:()=> 'm1',flash:msg=>events.push('flash:'+msg),markStorageBroken:()=>events.push('storage-error'),
  storageSnapshot:value=>JSON.parse(JSON.stringify(value)),roundStockQty:value=>Math.round((value+Number.EPSILON)*1000)/1000,
  publishAvailability:()=>events.push('availability-gate'),settleReturnedOrderLoyalty:()=>events.push('loyalty'),closeModal(){},render(){events.push('render')},
  currentShift:()=>state.shifts.find(s=>s.status==='open'),productTracksStock:p=>p?.type==='simple',fullMoney:String};
 context.window=context;
 context.webkit={messageHandlers:{storage:{postMessage(command){commands.push(command);
  if(command.action==='returnCommit'){events.push('native-command');return true}
  const result={requestId:command.requestId,ok:true,authoritative:true};
  if(command.action.endsWith('Status'))result.initialized=initialized.has(command.key);
  if(command.action.endsWith('Initialize'))initialized.add(command.key);
  context.__nativeStorageResult(result);return true;
 }}}};
 vm.createContext(context);vm.runInContext(source('native-storage-shadow.js'),context);
 vm.runInContext(source('Web/js/features/shifts.js'),context);vm.runInContext(source('native-shift-accounting.js'),context);
 vm.runInContext(source('Web/js/features/receipts.js'),context);vm.runInContext(source('native-payment-command.js'),context);vm.runInContext(source('native-return-command.js'),context);
 return {context,events,commands,timers,initial,reply(ok=true){const c=commands.filter(c=>c.action==='returnCommit').at(-1);events.push('ack');context.__nativeStorageResult({requestId:c.requestId,ok,authoritative:true})}};
}
async function flushUntil(predicate){for(let i=0;i<200&&!predicate();i++)await Promise.resolve();assert.ok(predicate())}
test('actual full return sends one receipt and defers stock/state/loyalty until native ack',async()=>{
 const h=host(),saving=h.context.processFullReturn('r1');await flushUntil(()=>h.events.includes('native-command'));
 const cmd=JSON.parse(h.commands.find(c=>c.action==='returnCommit').payload);
 assert.equal(cmd.expectedOrderCount,501);assert.equal(cmd.products[0].stock,0.3);assert.equal(cmd.refundMovement.amount,8);
 assert.equal(cmd.receipt.shiftId,'old');assert.equal(cmd.receipt.returnedShiftId,'new');assert.equal(Object.hasOwn(cmd,'orders'),false);
 assert.equal(h.context.state.products[0].stock,0.2);assert.equal(h.context.state.orders.at(-1).returnedAt,undefined);assert.equal(h.events.includes('loyalty'),false);
 h.reply();await saving;
 assert.equal(h.context.state.products[0].stock,0.3);assert.equal(h.context.cashDrawerBalance(h.context.state.shifts[1]),92);
 assert.ok(h.events.indexOf('loyalty')>h.events.indexOf('ack'));assert.ok(h.events.indexOf('availability-gate')>h.events.indexOf('ack'));
 await h.context.processFullReturn('r1');assert.equal(h.commands.filter(c=>c.action==='returnCommit').length,1);
});
test('failed native return leaves all business state unchanged and performs no effects',async()=>{
 const h=host(),saving=h.context.processFullReturn('r1');await flushUntil(()=>h.events.includes('native-command'));h.reply(false);await saving;
 assert.equal(JSON.stringify(h.context.state),JSON.stringify(h.initial));assert.equal(h.events.includes('loyalty'),false);assert.equal(h.events.includes('availability-gate'),false);assert.equal(h.events.includes('legacy-commit'),false);
});
test('uncertain return retries exact payload once and blocks subsequent commands until reload',async()=>{
 const h=host(),saving=h.context.processFullReturn('r1');await flushUntil(()=>h.events.includes('native-command'));
 const timeout=()=>{const [id,t]=[...h.timers].find(([,v])=>v.delay===15000);h.timers.delete(id);t.fn()};
 timeout();await flushUntil(()=>h.commands.filter(c=>c.action==='returnCommit').length===2);
 const req=h.commands.filter(c=>c.action==='returnCommit');assert.equal(req[0].payload,req[1].payload);
 timeout();await saving;assert.equal(h.context.criticalStorageRecoveryPending,true);
 await h.context.processFullReturn('r1');assert.equal(h.commands.filter(c=>c.action==='returnCommit').length,2);assert.equal(h.events.includes('loyalty'),false);
});
test('old receipts retain legacy restoration and journal; missing cash blocks before persistence',async()=>{
 const h=host({legacyReceipt:true});await h.context.processFullReturn('r1');assert.ok(h.events.includes('legacy-commit'));assert.equal(h.commands.some(c=>c.action==='returnCommit'),false);assert.equal(h.context.state.products[0].stock,1.2);
 const low=host();low.context.state.shifts[1].openingCash=7;await low.context.processFullReturn('r1');assert.equal(low.commands.some(c=>c.action==='returnCommit'),false);assert.equal(low.context.state.products[0].stock,0.2);
});
