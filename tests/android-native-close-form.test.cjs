const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const read=n=>fs.readFileSync('app/src/main/assets/pos/'+n,'utf8');
function host(){
 const sent=[],events=[],field={value:'100'},overlay={style:{}};let done;
 const ctx={state:{loaded:true,currency:'BYN'},criticalOperationBusy:false,criticalStorageRecoveryPending:false,currentShift:()=>({id:'s1'}),document:{getElementById:id=>id==='sf-counted'?field:null,querySelector:()=>overlay},openCloseShiftModal:()=>events.push('legacy-form'),openCashMovementModal:()=>events.push('cash-form'),submitCashMovement:async()=>true,closeModal:()=>events.push('close'),showModal:()=>events.push('show'),submitCloseShift:async()=>{events.push('submit');const ok=await new Promise(r=>done=r);if(ok){ctx.closeModal();events.push('telegram');events.push('print')}return ok},webkit:{messageHandlers:{shiftScreen:{postMessage:p=>{sent.push(p);return true}}}}};
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext(read('native-cash-forms.js'),ctx);vm.runInContext(read('native-close-form.js'),ctx);
 return {ctx,sent,events,field,overlay,reply:ok=>done(ok),open(){ctx.openCloseShiftModal();return sent.at(-1)},submit(p){return ctx.MPosCore.NativeCloseForm.handleAction(p)}};
}
test('closing passes zero and fractional counted cash once, closes only after ack and keeps outputs',async()=>{
 const h=host(),form=h.open();assert.equal(h.overlay.style.visibility,'hidden');assert.equal(Object.hasOwn(form,'expectedCash'),false);
 const p={action:'submit',token:form.token,type:'close',shiftId:'s1',amount:0};const saving=h.submit(p);await h.submit(p);
 assert.equal(h.field.value,'0');assert.equal(h.events.filter(e=>e==='submit').length,1);assert.equal(h.events.includes('close'),false);
 h.reply(true);await saving;assert.equal(h.events.filter(e=>e==='close').length,1);assert.ok(h.events.includes('telegram'));assert.ok(h.events.includes('print'));
});
test('known and uncertain failure preserve form and report proper status',async()=>{
 const h=host(),form=h.open(),saving=h.submit({action:'submit',token:form.token,type:'close',shiftId:'s1',amount:75.001});
 assert.equal(h.field.value,'75.001');h.ctx.criticalStorageRecoveryPending=true;h.reply(false);await saving;
 assert.equal(h.sent.at(-1).blocked,true);assert.equal(h.events.includes('close'),false);assert.equal(h.events.includes('telegram'),false);
});
test('stale, busy, changed shift or invalid amount cannot call existing submission',async()=>{
 const h=host(),form=h.open(),p={action:'submit',token:form.token,type:'close',shiftId:'s1',amount:1};
 for(const change of [{token:'old'},{type:'deposit'},{amount:-1},{amount:Infinity},{amount:'1'}])await h.submit({...p,...change});
 h.ctx.currentShift=()=>({id:'other'});await h.submit(p);h.ctx.currentShift=()=>({id:'s1'});h.ctx.criticalOperationBusy=true;await h.submit(p);
 assert.equal(h.events.includes('submit'),false);
});
test('cancel/replacement cancel generations; explicit fallback reveals original form for session',async()=>{
 const h=host(),form=h.open();await h.submit({action:'fallback',token:form.token});assert.equal(h.ctx.MPosNativeCloseFormEnabled,false);assert.equal(h.overlay.style.visibility,'');
 const count=h.sent.length;h.ctx.openCloseShiftModal();assert.equal(h.sent.length,count);
 h.ctx.MPosNativeCloseFormEnabled=true;const next=h.open();h.ctx.showModal('replacement');await h.submit({action:'submit',token:next.token});assert.equal(h.events.includes('submit'),false);
 const html=read('pos.html');assert.ok(html.indexOf('src="native-close-form.js"')>html.indexOf('src="native-cash-forms.js"'));
});
