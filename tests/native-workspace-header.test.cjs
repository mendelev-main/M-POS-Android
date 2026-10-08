const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-workspace-header.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const destinations=[['pos','M POS','brand'],['purchaseOrders','Заказы','tabs'],['receiving','Приёмка','tabs'],['receipts','Чеки','tabs'],['analytics','Аналитика','tabs'],['bookings','Бронирования','tabs'],['settings','Настройки','settings']];
function host(options={}){
 const sent=[],events=[],frames=[],discarded=[];let observer,modal=false,coveredPage=false,root;
 const node=(left,width)=>({style:{opacity:'',pointerEvents:''},isConnected:true,getBoundingClientRect:()=>({left,top:12,width,height:40}),click:()=>{throw Error('HTML click must not run')}});
 const groups={brand:node(16,96),tabs:node(122,800),settings:node(1050,44)};
 if(options.shift)groups.shift=node(925,120);
 root={isConnected:true,querySelector:s=>groups[{'.brand':'brand','.tabs':'tabs','.settings-topbar-btn':'settings','.shift-pill':'shift'}[s]]||null};
 const ctx={innerWidth:1200,innerHeight:800,document:{hidden:false,readyState:'complete',body:{},documentElement:{getAttribute:()=>options.dark?'dark':'light'},getElementById:id=>id==='app'?root:['modal-root','product-editor-root','payment-page-root'].includes(id)?{}:id==='printer-page'&&coveredPage?{}:null,querySelector:s=>s==='.topbar'?root:s.includes('modal-overlay')&&modal?{}:null,addEventListener(){}},requestAnimationFrame:fn=>frames.push(fn),addEventListener(){},MutationObserver:class{constructor(fn){observer=fn}observe(){}},render:()=>events.push('render'),flash:x=>events.push(x),setTab:tab=>{app().tab=tab;events.push('legacy')},onSearch:query=>{app().search=query},webkit:{messageHandlers:{workspace:{postMessage:p=>{sent.push(JSON.parse(JSON.stringify(p)));return options.refuse!==true}}}},MPosCore:{WorkspaceNavigationLifecycle:{generation:()=>ctx.generation||0,header:()=>options.read?options.read(model()):Promise.resolve(model())},WorkspaceNavigation:{execute:async p=>discarded.push(p)}}};
 function app(){return vm.runInContext('state',ctx)}
 if(options.shift){ctx.MPosCore.WorkspaceNavigationLifecycle.shiftHeader=()=>Promise.resolve({expected:{...app(),revision:1},shift:{tab:'shift',group:'shift',selected:app().tab==='shift',open:options.shift==='open',label:options.shift==='open'?'Иванов И.И.':'Открыть смену',revision:'saved-shift'}});ctx.MPosCore.NativeOpenForm={activeToken:()=>ctx.openingToken||null,openNative:()=>{events.push('native-open');ctx.openingToken='form';return true;}};}
 function model(){return{buttons:destinations.map(([tab,label,group])=>({tab,label,group,selected:app().tab===tab})),expected:{...app(),revision:1}}}
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext("let state={loaded:true,tab:'pos',search:'чай',posPath:'Кофе',posFolder:'',editMode:false,paymentPage:''}",ctx);
 vm.runInContext(source,ctx);
 const flush=()=>{for(let n=0;frames.length&&n<10;n++)frames.shift()()};flush();
 return{ctx,sent,events,discarded,groups,app,model,flush,mutate:()=>{observer();flush()},modal:value=>{modal=value},page:value=>{coveredPage=value},replaceGroup:()=>{groups.tabs=node(122,800)}};
}
test('native labels and selected tab use production lexical state and do not extract HTML actions',async()=>{
 const h=host();await tick();const show=h.sent[0];assert.equal(h.ctx.state,undefined);assert.equal(show.action,'headerShow');assert.equal(show.navigation.buttons[3].label,'Чеки');assert.equal(show.navigation.buttons[0].selected,true);
 assert.equal(h.groups.tabs.style.opacity,'0');h.mutate();await tick();assert.equal(h.sent.length,1);
 await h.ctx.__nativeWorkspaceHeaderResult({token:show.token,result:{ok:true,snapshot:{tab:'receipts'},headerToken:'accepted'}});
 assert.equal(h.app().tab,'receipts');assert.equal(h.app().search,'чай');assert.equal(h.app().posPath,'Кофе');assert.deepEqual(h.events,['render']);assert.equal(h.discarded.length,0);
});
test('saved shift pill selects shift tab and closed pill starts native form without HTML clicks',async()=>{
 for(const shift of ['open','closed']){
  const h=host({shift});await tick();const show=h.sent.find(p=>p.action==='headerShow');assert.ok(show.groups.shift);
  assert.equal(h.groups.shift.style.opacity,'0');assert.equal(show.navigation.shift.open,shift==='open');
  await h.ctx.__nativeWorkspaceHeaderResult({token:show.token,kind:'shift',result:{ok:true,effect:shift==='open'?'render':'openShift',snapshot:{tab:shift==='open'?'shift':'pos'},...(shift==='open'?{headerToken:'selected'}:{})}});
  assert.deepEqual(h.events,shift==='open'?['render']:['native-open']);assert.equal(h.app().tab,shift==='open'?'shift':'pos');
  if(shift==='closed'){assert.equal(h.sent.at(-2).action,'headerHide');assert.equal(h.groups.shift.style.opacity,'');}
 }
});
test('shift changes or native opening invalidate an old header result',async()=>{
 for(const change of [h=>{h.app().shifts=[{id:'new',status:'open'}]},h=>{h.ctx.openingToken='new-form'}]){
  const h=host({shift:'open'});await tick();const show=h.sent[0];change(h);
  await h.ctx.__nativeWorkspaceHeaderResult({token:show.token,kind:'shift',result:{ok:true,effect:'render',snapshot:{tab:'shift'},headerToken:'old'}});
  assert.equal(h.app().tab,'pos');assert.deepEqual(h.events,[]);assert.equal(h.discarded[0].headerToken,'old');
 }
});
test('stale tab reply is discarded and original controls restore under modal/payment/covered page',async()=>{
 for(const change of [h=>h.modal(true),h=>{h.app().paymentPage='main'},h=>h.page(true),h=>{h.ctx.document.hidden=true},h=>{h.ctx.generation=1},h=>h.replaceGroup()]){
  const h=host();await tick();const show=h.sent[0];change(h);
  await h.ctx.__nativeWorkspaceHeaderResult({token:show.token,result:{ok:true,snapshot:{tab:'receipts'},headerToken:'old'}});
  assert.equal(h.app().tab,'pos');assert.equal(h.discarded[0].operation,'discardHeaderTab');
 }
 const h=host();await tick();h.modal(true);h.mutate();await tick();assert.equal(h.sent.at(-1).action,'headerHide');assert.equal(h.groups.tabs.style.opacity,'');
});
test('slow header read cannot overlay payment or replace a newer read after old failure',async()=>{
 let reply;const h=host({read:()=>new Promise(r=>reply=r)});h.app().paymentPage='main';reply(h.model());await tick();assert.equal(h.sent.length,0);
 const pending=[];const a=host({read:model=>new Promise((resolve,reject)=>pending.push({resolve,reject,model}))});
 a.app().search='новый';a.mutate();pending[1].resolve(pending[1].model);await tick();assert.equal(a.sent.at(-1).action,'headerShow');
 pending[0].reject(Error('old read failed'));await tick();assert.equal(a.sent.at(-1).action,'headerShow');assert.equal(a.groups.tabs.style.opacity,'0');
});
test('explicit header rollback and native refusal preserve source buttons and auxiliary controls',async()=>{
 const h=host();await tick();h.ctx.MPosNativeWorkspaceHeaderEnabled=false;h.mutate();await tick();assert.equal(h.groups.brand.style.opacity,'');assert.equal(h.groups.settings.style.pointerEvents,'');
 const failed=host({refuse:true});await tick();assert.equal(failed.groups.tabs.style.opacity,'');
});
test('correlated completion uses original request token and cannot unlock a newer native action',async()=>{
 const h=host();await tick();await h.ctx.__nativeWorkspaceHeaderResult({token:'older',result:{ok:false}});
 assert.equal(h.sent.find(p=>p.action==='headerResult').requestToken,'older');assert.equal(h.events.length,0);
});

test('narrow or scrolled topbar preserves source scroller and reactivates native tabs when geometry fits',async()=>{
 const h=host();h.groups.settings.getBoundingClientRect=()=>({left:1250,top:12,width:44,height:40});await tick();h.mutate();await tick();
 assert.equal(h.groups.tabs.style.opacity,'');assert.notEqual(h.ctx.MPosNativeWorkspaceHeaderEnabled,false);
 h.groups.settings.getBoundingClientRect=()=>({left:1050,top:12,width:44,height:40});h.mutate();await tick();assert.equal(h.sent.at(-1).action,'headerShow');assert.equal(h.groups.tabs.style.opacity,'0');
});
