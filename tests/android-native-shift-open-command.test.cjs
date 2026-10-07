const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const script=fs.readFileSync('app/src/main/assets/pos/native-open-form.js','utf8');
function host(){
 const sent=[],events=[],secret={value:''},select={value:''},overlay={style:{}};
 const ctx={state:{loaded:true,currency:'BYN',shifts:[{id:'old',status:'closed',countedCash:80}],employees:[{id:'e1',name:'Кассир',role:'employee'}]},criticalOperationBusy:false,criticalStorageRecoveryPending:false,currentShift:()=>null,uid:()=> 'new-shift',
 document:{getElementById:id=>id==='sf-employee'?select:id==='sf-admin-password'?secret:null,querySelector:()=>overlay},openShiftModal:()=>events.push('form'),closeModal:()=>events.push('close'),showModal:()=>events.push('modal'),submitOpenShift:()=>{throw Error('JS verifier must not run')},render:()=>events.push('render'),sendTelegramShiftOpened:()=>events.push('telegram'),maybeSendMonthlyWarehouseReport:()=>events.push('monthly'),flash:s=>events.push(s),webkit:{messageHandlers:{shiftScreen:{postMessage:p=>{sent.push(structuredClone(p));return true}}}}};
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext(script,ctx);ctx.openShiftModal();const token=sent.at(-1).token;
 const action=p=>ctx.MPosCore.NativeOpenForm.handleAction({token,...p});
 const prepare=()=>action({action:'prepare',employeeId:'e1'});
 function committed(extra={}){const c=sent.find(p=>p.action==='openFormCommit');const shift={id:c.id,status:'open',openedAt:c.openedAt,employeeId:'e1',employeeName:'Кассир',openingCash:80};return {action:'committed',ok:true,shift,shifts:[...structuredClone(ctx.state.shifts),shift],...extra};}
 return {ctx,sent,events,secret,select,token,action,prepare,committed};
}
test('native opening waits for durable acknowledgement and never calls JS credential verifier',async()=>{
 const h=host();assert.equal(h.sent.at(-1).nativeCommit,true);await h.prepare();assert.equal(h.ctx.criticalOperationBusy,true);assert.equal(h.ctx.state.shifts.length,1);assert.deepEqual(h.events,['form']);assert.equal(h.secret.value,'');
 const c=h.sent.at(-1);assert.equal(c.action,'openFormCommit');assert.equal(Object.hasOwn(c,'password'),false);await h.action(h.committed());assert.equal(h.ctx.criticalOperationBusy,false);assert.equal(h.ctx.state.shifts.length,2);assert.deepEqual(h.events,['form','close','render','telegram','monthly']);assert.equal(h.sent.find(p=>p.action==='openFormResult').ok,true);
});
test('duplicate gestures and wrong tokens cannot dispatch a second opening or notification',async()=>{
 const h=host();await h.action({action:'prepare',token:'old',employeeId:'e1'});await h.prepare();await h.prepare();await h.action({action:'submit',employeeId:'e1',password:'synthetic'});assert.equal(h.sent.filter(p=>p.action==='openFormCommit').length,1);const reply=h.committed();await h.action(reply);await h.action(reply);assert.equal(h.events.filter(e=>e==='telegram').length,1);
});
test('cancellation and modal replacement cannot abandon an in-flight native opening',async()=>{
 const h=host();await h.prepare();assert.equal(h.ctx.closeModal(),false);assert.equal(h.ctx.showModal('new'),false);await h.action({action:'cancel'});assert.equal(h.events.includes('close'),false);assert.equal(h.ctx.criticalOperationBusy,true);await h.action(h.committed());assert.equal(h.ctx.criticalOperationBusy,false);
});
test('native rejection leaves state intact and permits credential correction without effects',async()=>{
 const h=host();await h.prepare();await h.action({action:'committed',ok:false,blocked:false,message:'Неверный пароль'});assert.equal(h.ctx.state.shifts.length,1);assert.equal(h.ctx.criticalOperationBusy,false);assert.equal(h.ctx.criticalStorageRecoveryPending,false);assert.equal(h.events.includes('telegram'),false);await h.prepare();assert.equal(h.sent.filter(p=>p.action==='openFormCommit').length,2);
});
test('changed live state, malformed success or pending recovery blocks further actions until restart',async()=>{
 for(const mode of ['state','employee','malformed','blocked']){
  const h=host();await h.prepare();let reply=h.committed();if(mode==='state')h.ctx.state.shifts.push({id:'other'});if(mode==='employee')h.ctx.state.employees[0].role='admin';if(mode==='malformed')reply.shift.id='wrong';if(mode==='blocked')reply={action:'committed',ok:false,blocked:true};await h.action(reply);assert.equal(h.ctx.criticalStorageRecoveryPending,true,mode);assert.equal(h.ctx.criticalOperationBusy,false);assert.equal(h.events.includes('telegram'),false);await h.prepare();assert.equal(h.sent.filter(p=>p.action==='openFormCommit').length,1);
 }
});
test('commit acknowledgement retains JSON fields regardless of native object-key order',async()=>{
 const h=host();await h.prepare();const reply=h.committed();reply.shifts[0]={countedCash:80,status:'closed',id:'old'};reply.shift={...reply.shift,custom:0};reply.shifts[1]=Object.fromEntries(Object.entries(reply.shift).reverse());await h.action(reply);assert.equal(h.ctx.state.shifts.length,2);assert.equal(h.ctx.criticalStorageRecoveryPending,false);
});
test('guard failures and explicit dispatch rejection do not start a transaction',async()=>{
 for(const mode of ['loaded','busy','shift','staff','recovery']){
  const h=host();if(mode==='loaded')h.ctx.state.loaded=false;if(mode==='busy')h.ctx.criticalOperationBusy=true;if(mode==='shift')h.ctx.currentShift=()=>({id:'open'});if(mode==='staff')h.ctx.state.employees=[];if(mode==='recovery')h.ctx.criticalStorageRecoveryPending=true;await h.prepare();assert.equal(h.sent.some(p=>p.action==='openFormCommit'),false,mode);
 }
 const h=host();h.ctx.webkit.messageHandlers.shiftScreen.postMessage=p=>{h.sent.push(p);return p.action!=='openFormCommit'};await h.prepare();assert.equal(h.ctx.criticalOperationBusy,false);assert.equal(h.ctx.criticalStorageRecoveryPending,false);
});
test('ambiguous dispatch failure cannot retry opening and external-effect failure keeps saved shift',async()=>{
 const h=host();h.ctx.webkit.messageHandlers.shiftScreen.postMessage=p=>{if(p.action==='openFormCommit')throw Error('unknown delivery');h.sent.push(p);return true};await h.prepare();assert.equal(h.ctx.criticalStorageRecoveryPending,true);assert.equal(h.ctx.state.shifts.length,1);
 const k=host();k.ctx.sendTelegramShiftOpened=()=>{throw Error('offline')};await k.prepare();await k.action(k.committed());assert.equal(k.ctx.state.shifts.length,2);assert.equal(k.ctx.criticalOperationBusy,false);assert.equal(k.ctx.criticalStorageRecoveryPending,false);
});
