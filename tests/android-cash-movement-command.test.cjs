const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/assets/pos'),source=n=>fs.readFileSync(path.join(root,n),'utf8');
function host(){
 const events=[],commands=[],timers=new Map(),initialized=new Set();let timerId=0;
 const state={orders:[],shifts:[{id:'old',status:'closed',openingCash:100,custom:true},{id:'s1',status:'open',openingCash:100,cashMovements:[],custom:'preserved'}]};
 const legacy=new Map(Object.entries(state)),fields={amount:'20',note:'  Комментарий  '},initial=structuredClone(state);
 const ctx={state,criticalOperationBusy:false,criticalStorageRecoveryPending:false,
  PrilavokCore:{Storage:{async get(key,fallback){return legacy.has(key)?structuredClone(legacy.get(key)):fallback},async set(key,v){legacy.set(key,v)},remove:key=>{events.push('cache:'+key);legacy.delete(key)},describe:()=>({})}},
  commitCriticalStorage:async()=>events.push('legacy-commit'),setTimeout(fn,delay){timers.set(++timerId,{fn,delay});return timerId},clearTimeout:id=>timers.delete(id),console:{error(){}},
  document:{getElementById:id=>({value:id==='cash-movement-amount'?fields.amount:fields.note})},uid:()=> 'm1',
  flash:msg=>events.push('flash:'+msg),markStorageBroken:()=>events.push('storage-error'),storageSnapshot:v=>JSON.parse(JSON.stringify(v)),closeModal:()=>events.push('close'),render:()=>events.push('render')};
 ctx.window=ctx;
 ctx.webkit={messageHandlers:{storage:{postMessage(command){commands.push(command);
  if(command.action==='cashMovementCommit'){events.push('native-command');return true}
  const result={requestId:command.requestId,ok:true,authoritative:true};
  if(command.action.endsWith('Status'))result.initialized=initialized.has(command.key);
  if(command.action.endsWith('Initialize'))initialized.add(command.key);
  ctx.__nativeStorageResult(result);return true;
 }}}};
 vm.createContext(ctx);vm.runInContext(source('native-storage-shadow.js'),ctx);vm.runInContext(source('Web/js/features/shifts.js'),ctx);vm.runInContext(source('native-shift-accounting.js'),ctx);vm.runInContext(source('native-cash-movement-command.js'),ctx);
 return {ctx,events,commands,timers,fields,initial,reply(ok=true){const cmd=commands.filter(c=>c.action==='cashMovementCommit').at(-1);events.push('ack');ctx.__nativeStorageResult({requestId:cmd.requestId,ok,authoritative:true})}};
}
async function flushUntil(predicate){for(let i=0;i<200&&!predicate();i++)await Promise.resolve();assert.ok(predicate())}
for(const type of ['deposit','withdrawal'])test('actual '+type+' waits for native persistence and preserves note/shift fields',async()=>{
 const h=host(),saving=h.ctx.submitCashMovement(type);await flushUntil(()=>h.events.includes('native-command'));
 const command=JSON.parse(h.commands.find(c=>c.action==='cashMovementCommit').payload);
 assert.equal(command.movement.type,type);assert.equal(command.movement.note,'Комментарий');assert.equal(command.movement.amount,20);assert.equal(Object.hasOwn(command,'orders'),false);
 assert.equal(h.ctx.state.shifts[1].cashMovements.length,0);assert.equal(h.events.includes('render'),false);
 assert.equal(await h.ctx.submitCashMovement(type),false);assert.equal(h.commands.filter(c=>c.action==='cashMovementCommit').length,1);
 h.reply();assert.equal(await saving,true);assert.equal(h.ctx.cashDrawerBalance(h.ctx.state.shifts[1]),type==='deposit'?120:80);
 assert.equal(h.ctx.state.shifts[1].custom,'preserved');assert.ok(h.events.indexOf('render')>h.events.indexOf('ack'));
 assert.deepEqual(h.events.filter(e=>e.startsWith('cache:')),['cache:shifts']);assert.equal(h.events.includes('legacy-commit'),false);
});
test('native failure keeps state/modal unchanged and never falls back to journal',async()=>{
 const h=host(),saving=h.ctx.submitCashMovement('deposit');await flushUntil(()=>h.events.includes('native-command'));h.reply(false);
 assert.equal(await saving,false);assert.equal(JSON.stringify(h.ctx.state),JSON.stringify(h.initial));assert.equal(h.events.includes('close'),false);assert.equal(h.events.includes('render'),false);assert.equal(h.events.includes('legacy-commit'),false);
});
test('unknown status retries identical payload once and unresolved status blocks another movement',async()=>{
 const h=host(),saving=h.ctx.submitCashMovement('withdrawal');await flushUntil(()=>h.events.includes('native-command'));
 const timeout=()=>{const [id,t]=[...h.timers].find(([,v])=>v.delay===15000);h.timers.delete(id);t.fn()};
 timeout();await flushUntil(()=>h.commands.filter(c=>c.action==='cashMovementCommit').length===2);
 const requests=h.commands.filter(c=>c.action==='cashMovementCommit');assert.equal(requests[0].payload,requests[1].payload);
 timeout();assert.equal(await saving,false);assert.equal(h.ctx.criticalStorageRecoveryPending,true);
 await h.ctx.submitCashMovement('deposit');assert.equal(h.commands.filter(c=>c.action==='cashMovementCommit').length,2);assert.equal(h.ctx.state.shifts[1].cashMovements.length,0);
});
test('comma decimals remain unrounded; invalid/insufficient/negative drawer cases stop before bridge',async()=>{
 const h=host();h.fields.amount='0,001';const saving=h.ctx.submitCashMovement('deposit');await flushUntil(()=>h.events.includes('native-command'));
 assert.equal(JSON.parse(h.commands.find(c=>c.action==='cashMovementCommit').payload).movement.amount,0.001);h.reply();assert.equal(await saving,true);
 for(const [type,amount,opening] of [['withdrawal','101',100],['deposit','0',100],['deposit','bad',100],['deposit','20',-1],['unknown','20',100]]){
  const bad=host();bad.fields.amount=amount;bad.ctx.state.shifts[1].openingCash=opening;
  assert.equal(await bad.ctx.submitCashMovement(type),false);assert.equal(bad.commands.some(c=>c.action==='cashMovementCommit'),false);
 }
});
test('cross-shift refund and old operation delegation retain reviewed boundaries',async()=>{
 const h=host();h.ctx.state.orders=[{id:'r1',shiftId:'old',method:'cash',total:20,returnedAt:1000,returnedShiftId:'s1',returnAmount:20}];
 h.ctx.state.shifts[1].cashMovements=[{id:'r1refund',type:'withdrawal',subtype:'refund',amount:20,timestamp:1000}];h.fields.amount='81';
 assert.equal(await h.ctx.submitCashMovement('withdrawal'),false);assert.equal(h.commands.some(c=>c.action==='cashMovementCommit'),false);
 await h.ctx.commitCriticalStorage('close-shift',{shifts:[]});assert.ok(h.events.includes('legacy-commit'));
});

for(const type of ['deposit','withdrawal'])test('native cash form uses actual handler and acknowledged Kotlin command for '+type,async()=>{
 const h=host(),forms=[],inputs={amount:{value:''},note:{value:''}};
 h.ctx.state.loaded=true;h.ctx.showModal=()=>h.events.push('modal');
 h.ctx.document={getElementById:id=>id==='cash-movement-amount'?inputs.amount:inputs.note,querySelector:()=>({style:{}})};
 h.ctx.webkit.messageHandlers.shiftScreen={postMessage:p=>{forms.push(p);return true}};
 vm.runInContext(source('native-cash-forms.js'),h.ctx);
 h.ctx.openCashMovementModal(type);const form=forms.at(-1);
 const saving=h.ctx.MPosCore.NativeCashForms.handleAction({action:'submit',token:form.token,type,shiftId:'s1',amount:0.001,note:'  native note  '});
 await flushUntil(()=>h.events.includes('native-command'));
 const command=JSON.parse(h.commands.find(c=>c.action==='cashMovementCommit').payload);
 assert.equal(command.movement.amount,0.001);assert.equal(command.movement.note,'native note');assert.equal(command.movement.type,type);
 assert.equal(h.ctx.state.shifts[1].cashMovements.length,0);assert.equal(h.events.includes('close'),false);
 h.reply();await saving;
 assert.equal(h.ctx.state.shifts[1].cashMovements.length,1);assert.equal(h.events.filter(e=>e==='close').length,1);
 assert.ok(forms.some(p=>p.action==='cashFormResult'&&p.ok));assert.equal(h.events.includes('legacy-commit'),false);
});
