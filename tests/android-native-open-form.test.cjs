const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const read=n=>fs.readFileSync('app/src/main/assets/pos/'+n,'utf8');
function host(){
 const sent=[],events=[],select={value:''},secret={value:''},overlay={style:{}};let done;
 const ctx={state:{loaded:true,currency:'BYN',employees:[{id:'e1',role:'cashier'}]},criticalOperationBusy:false,criticalStorageRecoveryPending:false,currentShift:()=>null,
 document:{getElementById:id=>id==='sf-employee'?select:id==='sf-admin-password'?secret:null,querySelector:()=>overlay},openShiftModal:()=>events.push('legacy-form'),closeModal:()=>events.push('close'),showModal:()=>events.push('show'),submitOpenShift:async()=>{events.push('submit');const ok=await new Promise(r=>done=r);if(ok){ctx.closeModal();events.push('telegram')}return ok},webkit:{messageHandlers:{shiftScreen:{postMessage:p=>{sent.push(p);return true}}}}};
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext(read('native-open-form.js'),ctx);
 return {ctx,sent,events,select,secret,overlay,reply:ok=>done(ok),open(){ctx.openShiftModal();return sent.at(-1)},submit(p){return ctx.MPosCore.NativeOpenForm.handleAction(p)}};
}
test('native opening delegates verification once and clears transient field on success and rejection',async()=>{
 for(const ok of [true,false]){
 const h=host(),form=h.open(),p={action:'submit',token:form.token,employeeId:'e1',password:'synthetic-invalid'};
 assert.equal(Object.hasOwn(form,'employees'),false);assert.equal(h.overlay.style.visibility,'hidden');const saving=h.submit(p);await h.submit(p);assert.equal(h.events.filter(e=>e==='submit').length,1);
 assert.equal(h.select.value,'e1');assert.equal(h.secret.value,'synthetic-invalid');h.reply(ok);await saving;
 assert.equal(h.secret.value,'');assert.equal(h.events.includes('close'),ok);assert.equal(h.events.includes('telegram'),ok);
 }
});
test('wrong token, missing employee, busy, loaded or already open guards prevent submission',async()=>{
 const h=host(),form=h.open(),p={action:'submit',token:form.token,employeeId:'e1',password:''};
 await h.submit({...p,token:'old'});await h.submit({...p,employeeId:'missing'});await h.submit({...p,password:null});
 h.ctx.criticalOperationBusy=true;await h.submit(p);h.ctx.criticalOperationBusy=false;h.ctx.currentShift=()=>({id:'s1'});await h.submit(p);
 assert.equal(h.events.includes('submit'),false);
});
test('cancel/replacement clears fields, fallback restores original session form, unknown status forwards reload gate',async()=>{
 const h=host(),form=h.open();h.secret.value='synthetic-invalid';await h.submit({action:'fallback',token:form.token});assert.equal(h.secret.value,'');assert.equal(h.overlay.style.visibility,'');assert.equal(h.ctx.MPosNativeOpenFormEnabled,false);
 h.ctx.MPosNativeOpenFormEnabled=true;const next=h.open(),saving=h.submit({action:'submit',token:next.token,employeeId:'e1',password:''});h.ctx.criticalStorageRecoveryPending=true;h.reply(false);await saving;assert.equal(h.sent.at(-1).blocked,true);
 h.ctx.showModal('another');assert.equal(h.sent.at(-1).action,'openFormHide');
 const html=read('pos.html');assert.ok(html.indexOf('src="native-open-form.js"')>html.indexOf('src="native-close-form.js"'));
});
