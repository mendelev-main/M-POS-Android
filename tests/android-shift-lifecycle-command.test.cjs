const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/assets/pos'),source=n=>fs.readFileSync(path.join(root,n),'utf8');
function host({opened=false}={}){
 const events=[],commands=[],timers=new Map(),initialized=new Set();let timerId=0;
 const state={orders:[],employees:[{id:'e1',name:'Кассир',phone:'test-phone',role:'cashier'}],currency:'₽',company:{},shifts:[{id:'old',status:'closed',closedAt:100,countedCash:80,custom:true}]};
 if(opened)state.shifts.push({id:'s1',status:'open',openedAt:200,openingCash:80,employeeId:'e1',employeeName:'Кассир',cashMovements:[],custom:'preserved'});
 const legacy=new Map(Object.entries(state)),fields={employee:'e1',counted:'75'},initial=structuredClone(state);
 const ctx={state,criticalOperationBusy:false,criticalStorageRecoveryPending:false,
  PrilavokCore:{Storage:{async get(key,fallback){return legacy.has(key)?structuredClone(legacy.get(key)):fallback},async set(key,v){legacy.set(key,v)},remove:key=>{events.push('cache:'+key);legacy.delete(key)},describe:()=>({})}},
  commitCriticalStorage:async()=>events.push('legacy-commit'),setTimeout(fn,delay){timers.set(++timerId,{fn,delay});return timerId},clearTimeout:id=>timers.delete(id),console:{error(){}},
  document:{getElementById:id=>({value:id==='sf-employee'?fields.employee:id==='sf-counted'?fields.counted:'synthetic-invalid'})},uid:()=> 's1',
  flash:msg=>events.push('flash:'+msg),markStorageBroken:()=>events.push('storage-error'),storageSnapshot:v=>JSON.parse(JSON.stringify(v)),closeModal:()=>events.push('close'),render:()=>events.push('render')};
 ctx.window=ctx;
 ctx.webkit={messageHandlers:{storage:{postMessage(command){commands.push(command);
  if(command.action==='shiftLifecycleCommit'){events.push('native-command');return true}
  const result={requestId:command.requestId,ok:true,authoritative:true};
  if(command.action.endsWith('Status'))result.initialized=initialized.has(command.key);
  if(command.action.endsWith('Initialize'))initialized.add(command.key);
  ctx.__nativeStorageResult(result);return true;
 }}}};
 vm.createContext(ctx);vm.runInContext(source('native-storage-shadow.js'),ctx);vm.runInContext(source('Web/js/features/shifts.js'),ctx);vm.runInContext(source('native-shift-accounting.js'),ctx);vm.runInContext(source('native-shift-lifecycle-command.js'),ctx);
 ctx.sendTelegramShiftOpened=()=>events.push('telegram-open');ctx.maybeSendMonthlyWarehouseReport=()=>events.push('monthly');
 ctx.sendTelegramShiftClosed=shift=>{events.push('telegram-close');events.push('report:'+ctx.buildShiftReportPayload(shift).expectedCash)};
 ctx.printShiftCloseReceipt=report=>{events.push('print');events.push('difference:'+report.difference)};
 return {ctx,events,commands,timers,fields,initial,reply(ok=true){const cmd=commands.filter(c=>c.action==='shiftLifecycleCommit').at(-1);events.push('ack');ctx.__nativeStorageResult({requestId:cmd.requestId,ok,authoritative:true})}};
}
async function flushUntil(predicate){for(let i=0;i<200&&!predicate();i++)await Promise.resolve();assert.ok(predicate())}
test('actual opening carries counted cash and starts reports only after native acknowledgement',async()=>{
 const h=host(),saving=h.ctx.submitOpenShift();await flushUntil(()=>h.events.includes('native-command'));
 const command=JSON.parse(h.commands.find(c=>c.action==='shiftLifecycleCommit').payload);
 assert.equal(command.operation,'open');assert.equal(command.shift.openingCash,80);assert.equal(command.shift.openingSourceShiftId,'old');assert.equal(command.shift.employeeName,'Кассир');assert.equal(command.expectedEmployees.length,1);assert.equal(Object.hasOwn(command,'orders'),false);
 assert.equal(h.ctx.state.shifts.length,1);assert.equal(h.events.includes('telegram-open'),false);assert.equal(await h.ctx.submitOpenShift(),false);
 h.reply();assert.equal(await saving,true);assert.equal(h.ctx.state.shifts.length,2);
 for(const event of ['render','telegram-open','monthly'])assert.ok(h.events.indexOf(event)>h.events.indexOf('ack'));
 assert.deepEqual(h.events.filter(e=>e.startsWith('cache:')),['cache:shifts']);assert.equal(h.events.includes('legacy-commit'),false);
 assert.equal(await h.ctx.submitOpenShift(),false);assert.equal(h.commands.filter(c=>c.action==='shiftLifecycleCommit').length,1);
});
test('actual closing persists counted cash before Telegram image payload and print report',async()=>{
 const h=host({opened:true});h.ctx.state.cart=[{productId:'unpaid',qty:1}];h.ctx.state.parked=[{id:'parked1',items:[{productId:'p1',qty:2}]}];
 const saving=h.ctx.submitCloseShift();await flushUntil(()=>h.events.includes('native-command'));
 const command=JSON.parse(h.commands.find(c=>c.action==='shiftLifecycleCommit').payload);
 assert.equal(command.operation,'close');assert.equal(command.expectedCash,80);assert.equal(command.expectedOrderCount,0);assert.equal(command.shift.countedCash,75);
 assert.equal(h.ctx.state.shifts[1].status,'open');assert.equal(h.events.includes('telegram-close'),false);assert.equal(h.events.includes('print'),false);
 h.reply();assert.equal(await saving,true);assert.equal(h.ctx.state.shifts[1].status,'closed');assert.equal(h.ctx.state.shifts[1].custom,'preserved');
 for(const event of ['render','telegram-close','print'])assert.ok(h.events.indexOf(event)>h.events.indexOf('ack'));
 assert.ok(h.events.includes('report:80'));assert.ok(h.events.includes('difference:-5'));
 assert.equal(h.ctx.state.cart[0].productId,'unpaid');assert.equal(h.ctx.state.parked[0].id,'parked1');
});
for(const opened of [false,true])test('native '+(opened?'close':'open')+' failure leaves state and external reports untouched',async()=>{
 const h=host({opened}),saving=opened?h.ctx.submitCloseShift():h.ctx.submitOpenShift();await flushUntil(()=>h.events.includes('native-command'));h.reply(false);assert.equal(await saving,false);
 assert.equal(JSON.stringify(h.ctx.state),JSON.stringify(h.initial));
 for(const event of ['render','telegram-open','monthly','telegram-close','print','legacy-commit'])assert.equal(h.events.includes(event),false);
});
test('unknown lifecycle status retries exact command once then blocks new critical action',async()=>{
 const h=host({opened:true}),saving=h.ctx.submitCloseShift();await flushUntil(()=>h.events.includes('native-command'));
 const timeout=()=>{const [id,t]=[...h.timers].find(([,v])=>v.delay===15000);h.timers.delete(id);t.fn()};
 timeout();await flushUntil(()=>h.commands.filter(c=>c.action==='shiftLifecycleCommit').length===2);
 const req=h.commands.filter(c=>c.action==='shiftLifecycleCommit');assert.equal(req[0].payload,req[1].payload);
 timeout();assert.equal(await saving,false);assert.equal(h.ctx.criticalStorageRecoveryPending,true);await h.ctx.submitCloseShift();assert.equal(h.commands.filter(c=>c.action==='shiftLifecycleCommit').length,2);assert.equal(h.events.includes('print'),false);
});
test('first shift zero, administrator rejection and invalid input preserve reviewed guards',async()=>{
 const first=host();first.ctx.state.shifts=[];const saving=first.ctx.submitOpenShift();await flushUntil(()=>first.events.includes('native-command'));
 const cmd=JSON.parse(first.commands.find(c=>c.action==='shiftLifecycleCommit').payload);assert.equal(cmd.shift.openingCash,0);assert.equal(cmd.shift.openingSourceShiftId,'');first.reply();assert.equal(await saving,true);
 const admin=host();admin.ctx.state.employees[0].role='admin';assert.equal(await admin.ctx.submitOpenShift(),false);assert.equal(admin.commands.some(c=>c.action==='shiftLifecycleCommit'),false);
 for(const counted of ['', '-1', 'bad']){const bad=host({opened:true});bad.fields.counted=counted;assert.equal(await bad.ctx.submitCloseShift(),false);assert.equal(bad.commands.some(c=>c.action==='shiftLifecycleCommit'),false)}
 const missing=host();missing.fields.employee='absent';assert.equal(await missing.ctx.submitOpenShift(),false);
});
test('cross-shift refunds and comma fractional counted cash feed the closing report consistently',async()=>{
 const h=host({opened:true});h.ctx.state.orders=[{id:'r1',shiftId:'old',method:'cash',total:20,returnedAt:250,returnedShiftId:'s1',returnAmount:20}];
 h.ctx.state.shifts[1].cashMovements=[{id:'refund',type:'withdrawal',subtype:'refund',amount:20,timestamp:250}];h.fields.counted='65,001';
 const saving=h.ctx.submitCloseShift();await flushUntil(()=>h.events.includes('native-command'));
 const cmd=JSON.parse(h.commands.find(c=>c.action==='shiftLifecycleCommit').payload);assert.equal(cmd.expectedCash,60);assert.equal(cmd.shift.countedCash,65.001);h.reply();assert.equal(await saving,true);
 assert.ok(h.events.includes('report:60'));assert.ok(h.events.some(e=>e.startsWith('difference:5.001')));
 await h.ctx.commitCriticalStorage('cash-movement',{shifts:[]});assert.ok(h.events.includes('legacy-commit'));
});
