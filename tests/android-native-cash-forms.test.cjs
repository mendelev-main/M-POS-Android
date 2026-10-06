const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-cash-forms.js','utf8');
function host(){
 const sent=[],events=[],fields={amount:{value:'',blur(){}},note:{value:''}},overlay={style:{}};let pending,done;
 const ctx={state:{loaded:true},criticalOperationBusy:false,criticalStorageRecoveryPending:false,currentShift:()=>({id:'s1'}),document:{getElementById:id=>id==='cash-movement-amount'?fields.amount:id==='cash-movement-note'?fields.note:null,querySelector:()=>overlay},openCashMovementModal:t=>events.push('legacy-form:'+t),closeModal:()=>events.push('close'),showModal:()=>events.push('show'),markStorageBroken:()=>events.push('broken'),submitCashMovement:async type=>{events.push('submit:'+type);pending=new Promise(r=>done=r);const ok=await pending;if(ok)ctx.closeModal();return ok;},webkit:{messageHandlers:{shiftScreen:{postMessage:p=>{sent.push(p);return true}}}}};
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext(source,ctx);
 return {ctx,sent,events,fields,overlay,open(type='deposit'){ctx.openCashMovementModal(type);return sent.at(-1);},reply:ok=>done(ok),submit(p){return ctx.MPosCore.NativeCashForms.handleAction(p)}};
}
test('native form hides compatibility fields and applies values once before acknowledged close',async()=>{
 const h=host(),form=h.open();assert.equal(form.type,'deposit');assert.equal(h.overlay.style.visibility,'hidden');
 const p={action:'submit',token:form.token,shiftId:'s1',type:'deposit',amount:0.001,note:'test'};
 const saving=h.submit(p);assert.equal(h.fields.amount.value,'0.001');assert.equal(h.fields.note.value,'test');await h.submit(p);assert.equal(h.events.filter(x=>x==='submit:deposit').length,1);
 h.ctx.closeModal();assert.equal(h.events.includes('close'),false);h.reply(true);await saving;assert.equal(h.events.filter(x=>x==='close').length,1);assert.ok(h.sent.some(p=>p.action==='cashFormResult'&&p.ok));
});
test('failure preserves form; uncertain failure blocks native retry; cancel dismisses without submit',async()=>{
 const h=host(),form=h.open('withdrawal'),saving=h.submit({action:'submit',token:form.token,shiftId:'s1',type:'withdrawal',amount:2,note:''});
 h.ctx.criticalStorageRecoveryPending=true;h.reply(false);await saving;assert.equal(h.sent.at(-1).blocked,true);assert.equal(h.events.includes('close'),false);
 await h.submit({action:'cancel',token:form.token});assert.equal(h.events.at(-1),'close');
});
test('stale token, changed shift, busy operation and invalid fields cannot submit',async()=>{
 const h=host(),form=h.open();const p={action:'submit',token:form.token,shiftId:'s1',type:'deposit',amount:1,note:''};
 await h.submit({...p,token:'stale'});await h.submit({...p,type:'withdrawal'});await h.submit({...p,amount:-1});await h.submit({...p,note:null});
 h.ctx.currentShift=()=>({id:'changed'});await h.submit(p);h.ctx.currentShift=()=>({id:'s1'});h.ctx.criticalOperationBusy=true;await h.submit(p);
 assert.equal(h.events.some(x=>x.startsWith('submit:')),false);
});
test('legacy rollback stays visible and replacement modal cancels the native generation',async()=>{
 const h=host();h.ctx.MPosNativeCashFormsEnabled=false;h.ctx.openCashMovementModal('deposit');assert.equal(h.sent.length,0);assert.equal(h.overlay.style.visibility,undefined);
 h.ctx.MPosNativeCashFormsEnabled=true;const first=h.open();h.ctx.showModal('another');assert.equal(h.sent.at(-1).action,'cashFormHide');await h.submit({action:'submit',token:first.token});assert.equal(h.events.some(x=>x.startsWith('submit:')),false);
 const html=fs.readFileSync('app/src/main/assets/pos/pos.html','utf8');assert.ok(html.indexOf('src="native-cash-forms.js"')>html.indexOf('src="native-shift-screen.js"'));
});
