const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const root='app/src/main/assets/pos/',html=fs.readFileSync(root+'pos.html','utf8');
const helpers=html.slice(html.indexOf('function storageRecordArray('),html.indexOf('function storageSnapshot('));
const shiftSource=fs.readFileSync(root+'Web/js/features/shifts.js','utf8');
const selectors=shiftSource.slice(0,shiftSource.indexOf('function shiftOrders('));
const plain=v=>JSON.parse(JSON.stringify(v));
function reviewed(input){const warnings=[];const ctx={state:{},markStorageBroken:s=>warnings.push(s),storageSnapshot:plain};vm.createContext(ctx);vm.runInContext(helpers+selectors,ctx);ctx.state.shifts=ctx.storageRecordArray(input.shifts.found?structuredClone(input.shifts.value):[],'shifts');ctx.state.employees=ctx.storageRecordArray(input.employees.found?structuredClone(input.employees.value):[],'employees');ctx.state.employees.forEach(e=>e.role=e.role==='admin'?'admin':'employee');const shift=ctx.currentShift();const employee=shift?ctx.state.employees.find(e=>e.id===shift.employeeId):null;return {shifts:plain(ctx.state.shifts),employees:plain(ctx.state.employees),warnings,activeShiftIndex:shift?ctx.state.shifts.indexOf(shift):null,activeEmployeeIndex:employee?ctx.state.employees.indexOf(employee):null,isAdmin:ctx.currentShiftEmployeeIsAdmin()};}
const envelope=value=>({found:true,value}),missing={found:false};
const cases=[
 {name:'missing documents',shifts:missing,employees:missing},
 {name:'stored null differs from missing',shifts:envelope(null),employees:envelope(null)},
 {name:'first open shift and exact admin role',shifts:envelope([{status:'closed',employeeId:'old'},{status:'open',employeeId:'a',extension:false},{status:'open',employeeId:'b'}]),employees:envelope([{id:'a',role:'admin',extension:{zero:0}},{id:'b',role:'admin'}])},
 {name:'first matching employee retains duplicate ID precedence',shifts:envelope([{status:'open',employeeId:'a'}]),employees:envelope([{id:'a',role:'ADMIN'},{id:'a',role:'admin'}])},
 {name:'invalid records filtered and unknown roles normalized',shifts:envelope([null,1,[],{status:'open',employeeId:'a'}]),employees:envelope([false,null,[],{id:'a',role:null,custom:0}])},
 {name:'numeric ID does not match string',shifts:envelope([{status:'open',employeeId:1}]),employees:envelope([{id:'1',role:'admin'},{id:1,role:'employee'}])},
 {name:'absent IDs match absent not JSON null',shifts:envelope([{status:'open'}]),employees:envelope([{id:null,role:'admin'},{role:'employee'}])},
 {name:'null IDs match null not absent',shifts:envelope([{status:'open',employeeId:null}]),employees:envelope([{role:'admin'},{id:null,role:'employee'}])},
 {name:'object IDs never match by JSON equality',shifts:envelope([{status:'open',employeeId:{id:'a'}}]),employees:envelope([{id:{id:'a'},role:'admin'}])},
 {name:'no open shift cannot grant admin',shifts:envelope([{status:'OPEN',employeeId:'a'},{status:'closed',employeeId:'a'}]),employees:envelope([{id:'a',role:'admin'}])},
 {name:'malformed containers',shifts:envelope({status:'open'}),employees:envelope('bad')},
];
if(process.argv.includes('--write-fixtures'))fs.writeFileSync('tests/fixtures/active-session.json',JSON.stringify(cases.map(c=>({name:c.name,input:{version:1,shifts:c.shifts,employees:c.employees},expected:reviewed(c)})),null,2)+'\n');
else{
 const fixtures=JSON.parse(fs.readFileSync('tests/fixtures/active-session.json','utf8'));
 test('active session fixtures match actual normalization and synchronous role selectors',()=>{for(const c of fixtures)assert.deepEqual(reviewed(c.input),c.expected,c.name)});
 function adapter(mode){const script=fs.readFileSync(root+'native-storage-shadow.js','utf8');const block=script.slice(script.indexOf('  mposCore.ActiveSession='),script.indexOf('  mposCore.SessionRestore='));const calls=[],global={},mposCore={};const ctx={global,mposCore,initializeNative:async key=>calls.push('init:'+key),request:async action=>{calls.push(action);if(mode==='error')throw Error('read failed');if(mode==='lateRollback')global.MPosNativeActiveSessionEnabled=false;return mode==='invalid'?{ok:true,authoritative:true}: {ok:true,authoritative:true,...fixtures[2].expected}},requireNative:r=>{if(!r.ok||!r.authoritative)throw Error('authority');return r}};vm.createContext(ctx);vm.runInContext(block,ctx);return {global,mposCore,calls};}
 test('bootstrap initializes existing authority before one paired native read',async()=>{const h=adapter('ok'),r=await h.mposCore.ActiveSession.bootstrap();assert.deepEqual(h.calls,['init:shifts','init:employees','init:criticalStorageJournal','rootSessionBootstrap']);assert.equal(r.activeShiftIndex,1);assert.equal(r.isAdmin,true)});
 test('failed malformed or disabled bootstrap preserves reviewed per-key fallback',async()=>{for(const mode of ['error','invalid','lateRollback','rollback']){const h=adapter(mode);if(mode==='rollback')h.global.MPosNativeActiveSessionEnabled=false;assert.equal(await h.mposCore.ActiveSession.bootstrap(),null);if(mode==='rollback')assert.deepEqual(h.calls,[])}});
 test('actual loadAll prefix applies paired records or reviewed fallback with identical warnings',async()=>{
  for(const c of fixtures)for(const native of [true,false]){
   const warnings=[],reads=[],state={};const ctx={state,hasCloudStorage:true,recoverCriticalStorageJournal:async()=>{},MPosCore:{ActiveSession:{bootstrap:async()=>native?structuredClone(c.expected):null}},markStorageBroken:s=>warnings.push(s),storageSnapshot:plain,loadKey:async(key,fallback)=>{reads.push(key);if(key==='shifts'||key==='employees')return c.input[key].found?structuredClone(c.input[key].value):fallback;return key==='products'?[]:fallback},seedProducts:()=>[]};ctx.window=ctx;vm.createContext(ctx);
   const start=html.indexOf('async function loadAll(){'),end=html.indexOf('  state.orders = storageRecordArray',start);
   vm.runInContext(helpers+html.slice(start,end)+'}',ctx);await ctx.loadAll();
   assert.deepEqual(plain(state.shifts),c.expected.shifts,c.name);assert.deepEqual(plain(state.employees),c.expected.employees,c.name);assert.deepEqual(warnings,c.expected.warnings,c.name);
   assert.equal(reads.includes('shifts'),!native);assert.equal(reads.includes('employees'),!native);
  }
 });
 test('production paired reads occur after critical journal recovery and before record normalization',()=>{const start=html.indexOf('async function loadAll(){'),end=html.indexOf('  state.orders = storageRecordArray',start),block=html.slice(start,end);assert.ok(block.indexOf('await recoverCriticalStorageJournal()')<block.indexOf('ActiveSession?.bootstrap()'));assert.match(block,/nativeActiveSession \? nativeActiveSession\.shifts : loadKey\('shifts', \[\]\)/);assert.match(block,/nativeActiveSession \? nativeActiveSession\.employees : loadKey\('employees', \[\]\)/);assert.ok(block.indexOf('warnings.forEach(markStorageBroken)')<block.indexOf('state.shifts = storageRecordArray'));});
}
