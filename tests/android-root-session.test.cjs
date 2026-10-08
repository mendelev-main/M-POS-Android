const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const script=fs.readFileSync('app/src/main/assets/pos/native-root-session.js','utf8');
const fixtures=JSON.parse(fs.readFileSync('tests/fixtures/active-session.json','utf8'));
const copy=v=>JSON.parse(JSON.stringify(v));
function setup(execute=async()=>{},load=async()=>{}){
 const ctx={MPosCore:{RootStartup:{execute}},currentShift:()=>({legacy:true}),currentShiftEmployeeIsAdmin:()=>true,loadAll:load};ctx.window=ctx;
 vm.createContext(ctx);vm.runInContext(script,ctx);return ctx;
}
test('Android synchronous consumers use detached Kotlin selections for every parity fixture',()=>{
 for(const f of fixtures){const h=setup(),m=f.expected;h.MPosCore.RootSession.receive(m,1);
  assert.deepEqual(copy(h.currentShift()),m.activeShiftIndex===null?null:m.shifts[m.activeShiftIndex],f.name);
  assert.equal(h.currentShiftEmployeeIsAdmin(),m.isAdmin,f.name);
  assert.deepEqual(copy(h.MPosCore.RootSession.selectedEmployee()),m.activeEmployeeIndex===null?null:m.employees[m.activeEmployeeIndex]);
  const shift=h.currentShift();if(shift)shift.status='mutated';assert.deepEqual(copy(h.currentShift()),m.activeShiftIndex===null?null:m.shifts[m.activeShiftIndex]);
 }
});
test('late replies cannot restore old admin or reopened shift; invalid model fails closed',()=>{
 const h=setup();h.MPosCore.RootSession.receive(fixtures[2].expected,10);assert.equal(h.currentShiftEmployeeIsAdmin(),true);
 h.MPosCore.RootSession.receive(fixtures[0].expected,11);h.MPosCore.RootSession.receive(fixtures[2].expected,10);
 assert.equal(h.currentShift(),null);assert.equal(h.currentShiftEmployeeIsAdmin(),false);
 h.MPosCore.RootSession.receive(null,12);assert.equal(h.currentShiftEmployeeIsAdmin(),false);
 h.MPosNativeActiveSessionEnabled=false;assert.equal(h.currentShift().legacy,true);
});
test('startup clears previous selection until Kotlin completes recovery; explicit rollback remains',async()=>{
 const calls=[],h=setup(async input=>{calls.push(input);return input.operation==='begin'?{generation:3,step:'recover'}:{generation:3,step:input.completed==='recover'?'hydrate':input.completed==='hydrate'?'activate':'ready',rootSession:fixtures[2].expected}});
 h.MPosCore.RootSession.receive(fixtures[2].expected,1);const ticket=await h.MPosCore.RootSession.begin();assert.equal(h.currentShift(),null);
 await h.MPosCore.RootSession.recovered(ticket);assert.equal(h.currentShiftEmployeeIsAdmin(),true);
 await h.MPosCore.RootSession.hydrated(ticket);await h.MPosCore.RootSession.activated(ticket);
 assert.deepEqual(calls.map(c=>c.completed||c.operation),['begin','recover','hydrate','activate']);
 h.MPosNativeActiveSessionEnabled=false;assert.equal(await h.MPosCore.RootSession.begin(),null);
});
test('failed import/startup does not poison the next load and concurrent loads are serialized',async()=>{
 const calls=[];let unblock;const first=new Promise(resolve=>unblock=resolve);let count=0;
 const h=setup(undefined,async()=>{const n=++count;calls.push(n);if(n===1){await first;throw Error('read failed')}return n});
 const a=h.loadAll(),b=h.loadAll();const rejected=assert.rejects(a,/read failed/);await Promise.resolve();assert.deepEqual(calls,[1]);unblock();await rejected;assert.equal(await b,2);assert.deepEqual(calls,[1,2]);assert.equal(h.currentShift(),null);
});
test('invalid native startup phase cannot activate legacy fallback silently',async()=>{
 const h=setup(async()=>({step:'ready',rootSession:fixtures[2].expected}));
 await assert.rejects(h.MPosCore.RootSession.recovered({generation:1}),/phase/);
 assert.equal(h.currentShiftEmployeeIsAdmin(),false);
});
test('compact mutation reply replaces the selected employee and shift without history arrays',()=>{
 const h=setup();const model={currentShift:{id:'new',status:'open',employeeId:'b'},selectedEmployee:{id:'b',role:'employee'},isAdmin:false,recoveryPending:false};
 h.MPosCore.RootSession.receive(fixtures[2].expected,1);h.MPosCore.RootSession.receive(model,2);
 assert.deepEqual(copy(h.currentShift()),model.currentShift);assert.deepEqual(copy(h.MPosCore.RootSession.selectedEmployee()),model.selectedEmployee);assert.equal(h.currentShiftEmployeeIsAdmin(),false);
 model.currentShift.id='mutated';assert.equal(h.currentShift().id,'new');
});
test('storage acknowledgement applies root state before resolving the awaiting business handler',()=>{
 const source=fs.readFileSync('app/src/main/assets/pos/native-storage-shadow.js','utf8');
 const block=source.slice(source.indexOf('  global.__nativeStorageResult='),source.indexOf('  setTimeout(mirrorExistingLocalStorage',source.indexOf('  global.__nativeStorageResult=')));
 const h=setup(),events=[],pending=new Map([['save',{timer:1,resolve:r=>{events.push('ack');assert.equal(h.currentShiftEmployeeIsAdmin(),false);assert.equal(h.currentShift(),null);assert.equal(r.ok,true)}}]]);
 h.MPosCore.RootSession.receive(fixtures[2].expected,1);
 const original=h.MPosCore.RootSession.receive;h.MPosCore={RootSession:{receive:(...args)=>{events.push('root');original(...args)}}};
 const ctx={global:h,pending,clearTimeout:()=>{}};vm.createContext(ctx);vm.runInContext(block,ctx);
 h.__nativeStorageResult({requestId:'save',ok:true,rootSession:{currentShift:null,selectedEmployee:null,isAdmin:false},rootSequence:2});
 assert.deepEqual(events,['root','ack']);assert.equal(pending.size,0);
});

test('root restart invalidates workspace before recovery and releases it only after activation',async()=>{
 const events=[],h=setup(async input=>{events.push(input.operation);return{generation:1,step:input.operation==='begin'?'recover':'ready'}});
 h.MPosCore.WorkspaceNavigationLifecycle={invalidate:()=>events.push('invalidate'),ready:()=>events.push('ready')};
 const ticket=await h.MPosCore.RootSession.begin();assert.deepEqual(events,['invalidate','begin']);
 await h.MPosCore.RootSession.activated(ticket);assert.deepEqual(events,['invalidate','begin','advance','ready']);
});
