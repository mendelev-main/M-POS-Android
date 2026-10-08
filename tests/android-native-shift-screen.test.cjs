const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-shift-screen.js','utf8');
function setup(){
 const sent=[],calls=[],frames=[],listeners={};let modal=false,observer,hiddenModal=false;
 const ctx={state:{loaded:true,tab:'shift',shifts:[{id:'s1'}],orders:[],currency:'BYN',company:{establishmentName:'Test'}},renderShiftScreen:()=>'<div class="screen content-screen active">',currentShift:()=>ctx.state.shifts[0],flash:(...a)=>calls.push(['flash',...a]),openShiftModal:()=>{calls.push(['open']);modal=true},openCloseShiftModal:()=>{calls.push(['close']);modal=true},openCashMovementModal:t=>{calls.push([t]);modal=true},viewShiftModal:id=>{calls.push(['report',id]);modal=true},Promise,requestAnimationFrame:f=>frames.push(f),MutationObserver:class{constructor(f){observer=f}observe(){}},document:{documentElement:{getAttribute:()=>ctx.theme||'light'},readyState:'complete',querySelector:q=>q.includes('modal')?(modal?{style:{visibility:hiddenModal?'hidden':''}}:null):{getBoundingClientRect:()=>({left:0,top:60,width:1000,height:640})},getElementById:()=>({}),addEventListener(){}},innerWidth:1000,innerHeight:700,addEventListener:(name,fn)=>{listeners[name]=fn},webkit:{messageHandlers:{shiftScreen:{postMessage:p=>sent.push(p)}}}};
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext(source,ctx);
 return {ctx,sent,calls,listeners,flush:()=>{while(frames.length)frames.shift()()},change:()=>observer(),modal:(v,hidden=false)=>{modal=v;hiddenModal=hidden}};
}
test('native shift bounds and presentation exclude financial mirrors and duplicate reads',()=>{
 const h=setup();h.flush();assert.equal(h.sent.length,1);assert.equal(h.sent[0].action,'show');assert.equal(h.sent[0].rect.top,60);assert.equal(h.sent[0].currency,'BYN');assert.ok(!('orders' in h.sent[0]));assert.match(h.ctx.renderShiftScreen(),/data-mpos-shift-screen/);
 h.change();h.flush();assert.equal(h.sent.length,2);h.ctx.state.orders=[];h.change();h.flush();assert.equal(h.sent.length,3);h.ctx.state.shifts[0].openingCash=55;h.ctx.renderShiftScreen();h.change();h.flush();assert.equal(h.sent.length,4);
 h.ctx.state.tab='sale';h.change();h.flush();assert.equal(h.sent.at(-1).action,'hide');
});
test('existing forms retain actions, stale shifts reject, modal pauses native screen',()=>{
 const h=setup();h.flush();h.ctx.__mposShiftScreenAction({action:'deposit',shiftId:'other'});h.flush();assert.equal(h.calls[0][0],'flash');
 h.ctx.__mposShiftScreenAction({action:'withdrawal',shiftId:'s1'});h.flush();assert.equal(h.calls.at(-1)[0],'withdrawal');
 h.ctx.__mposShiftScreenAction({action:'close',shiftId:'s1'});assert.equal(h.calls.at(-1)[0],'withdrawal');
 h.modal(false);h.change();h.flush();assert.equal(h.sent.at(-1).action,'show');
 h.ctx.__mposShiftScreenAction({action:'fallback'});h.flush();assert.equal(h.sent.at(-1).action,'hide');assert.equal(h.ctx.MPosNativeShiftScreenEnabled,false);
});
test('unloaded state and busy action do not execute business commands; scripts ordered',()=>{
 const h=setup();h.ctx.state.loaded=false;h.flush();assert.equal(h.sent.length,0);h.ctx.state.loaded=true;h.ctx.state.busy=true;h.ctx.__mposShiftScreenAction({action:'open'});assert.equal(h.calls.length,0);h.ctx.__mposShiftScreenAction({action:'evil'});assert.equal(h.calls.length,0);
 const html=fs.readFileSync('app/src/main/assets/pos/pos.html','utf8');assert.ok(html.indexOf('src="native-shift-screen.js"')>html.indexOf('src="native-shift-accounting.js"'));assert.ok(html.indexOf('src="native-shift-screen.js"')>html.indexOf('src="native-shift-reports.js"'));
});

test('changing POS theme refreshes native palette without financial payloads',()=>{const h=setup();h.flush();assert.equal(h.sent.at(-1).theme,'light');h.ctx.theme='dark';h.change();h.flush();assert.equal(h.sent.at(-1).theme,'dark');assert.ok(!('orders' in h.sent.at(-1)));});

test('native cash dialog keeps shift background mounted and blocked without duplicate financial reads',()=>{
 const h=setup();h.flush();const before=h.sent.length;
 h.ctx.openCashMovementModal=t=>{h.calls.push([t]);h.modal(true,true)};
 h.ctx.__mposShiftScreenAction({action:'deposit',shiftId:'s1'});h.flush();
 assert.deepEqual(h.sent.slice(before).map(p=>p.action),['block']);assert.equal(h.sent.at(-1).blocked,true);
 h.ctx.__mposShiftScreenAction({action:'withdrawal',shiftId:'s1'});assert.deepEqual(h.calls,[['deposit']]);
 h.modal(false);h.change();h.flush();assert.equal(h.sent.at(-1).action,'block');assert.equal(h.sent.at(-1).blocked,false);
 h.modal(true);h.change();h.flush();assert.equal(h.sent.at(-1).action,'hide');
});
test('native opening token blocks retained shift screen and unblocks on cancel without HTML overlay',()=>{
 const h=setup();let token=null;h.ctx.MPosCore={NativeOpenForm:{activeToken:()=>token}};h.flush();const before=h.sent.length;
 token='opening';h.listeners['mpos-native-open-state']();h.flush();
 assert.deepEqual(h.sent.slice(before).map(packet=>packet.action),['block']);assert.equal(h.sent.at(-1).blocked,true);
 h.ctx.__mposShiftScreenAction({action:'open'});h.flush();assert.equal(h.calls.length,0);
 token=null;h.listeners['mpos-native-open-state']();h.flush();assert.equal(h.sent.at(-1).action,'block');assert.equal(h.sent.at(-1).blocked,false);
});
