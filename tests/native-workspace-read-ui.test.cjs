const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-workspace.js','utf8'),tick=()=>new Promise(r=>setImmediate(r));
function host(read,configure){const sent=[],calls=[],frames=[],events=[];let observer;
 const node=()=>({style:{opacity:'',pointerEvents:''},classList:{contains:s=>s==='active'},getBoundingClientRect:()=>({left:0,top:80,width:1200,height:700}),querySelector:s=>s==='.product-grid'?grid:panel,querySelectorAll(){throw Error('Business HTML extraction is forbidden')}}),grid=node(),panel=node(),root=node();
 const c={MPosCore:{WorkspaceNavigationLifecycle:{generation:()=>c.runtime||0,toolbar:async()=>{}},WorkspaceNavigation:{execute:async input=>{calls.push(input);return read?read(input):model(input)}}},webkit:{messageHandlers:{workspace:{postMessage:p=>{sent.push(p);return true}}}},innerWidth:1200,innerHeight:800,
 requestAnimationFrame:fn=>frames.push(fn),MutationObserver:class{constructor(fn){observer=fn}observe(){}},addEventListener(){},getComputedStyle:()=>({gridTemplateColumns:'1fr 1fr 1fr 1fr 1fr',paddingLeft:'16px',columnGap:'12px',gridTemplateRows:'155px',flexDirection:'row'}),document:{readyState:'complete',hidden:false,getElementById:id=>id==='screen-pos'?root:id==='app'?{}:null,querySelector:()=>null,body:{},documentElement:{getAttribute:()=>null},addEventListener(){}},
 addToCart:id=>events.push(['add',id]),removeFromCart:id=>events.push(['remove',id]),openCartItemModal:id=>events.push(['edit',id]),openPaymentModal:()=>events.push(['payment']),render:()=>events.push(['render']),flash:m=>events.push(['flash',m])};c.window=c;
 function model(input){return{ok:true,authoritative:true,model:{navigation:{expected:input.expected,title:'Room'},tiles:[],lines:[],catalogScope:[{type:'product',id:'p'}],actions:{0:{operation:'addProduct',value:'p'},1:{operation:'removeCartLine',value:'line'},2:{operation:'editCartLine',value:'line'}}}};}
 if(configure)configure(c,events);vm.createContext(c);vm.runInContext("let state={loaded:true,tab:'pos',search:'',posPath:null,posFolder:'',editMode:false,paymentPage:'',cart:[],products:[],shifts:[],parked:[],currency:'BYN',orderType:'На месте',customer:{},deliveryFee:0}",c);vm.runInContext(source,c);
 const flush=()=>{for(let n=0;frames.length&&n<10;n++)frames.shift()()};flush();return{c,sent,calls,events,root,model,flush,mutate:()=>{observer();flush()},state:()=>vm.runInContext('state',c)};}
test('default workspace reads domain model and dispatches typed commands without HTML targets',async()=>{
 const h=host();await tick();assert.equal(h.calls[0].operation,'workspaceView');assert.equal(h.sent[0].model.navigation.title,'Room');assert.equal(h.root.style.opacity,'0');
 await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'0'});assert.deepEqual(h.events,[['add','p']]);h.flush();await tick();
 await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'1'});assert.deepEqual(h.events,[['add','p'],['remove','line']]);
});
test('stale order/runtime and unsupported action cannot mutate another order',async()=>{
 for(const change of [h=>h.state().cart.push({productId:'new'}),h=>{h.c.runtime=1},h=>{h.state().paymentPage='main'}]){
  const h=host();await tick();const token=h.sent[0].token;change(h);await h.c.__nativeWorkspaceAction({token,action:'click',key:'0'});assert.deepEqual(h.events,[]);
 }
 const h=host();await tick();await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'__proto__'});assert.deepEqual(h.events,[]);
});
test('late read cannot hide newer native UI; invalidation restores source and drops pending reads',async()=>{
 const pending=[],h=host(input=>new Promise((resolve,reject)=>pending.push({input,resolve,reject})));await tick();h.state().search='new';h.mutate();await tick();
 pending[1].resolve(h.model(pending[1].input));await tick();pending[0].reject(Error('old'));await tick();assert.equal(h.root.style.opacity,'0');
 h.c.MPosCore.WorkspaceReadUi.invalidate();assert.equal(h.root.style.opacity,'');assert.equal(h.sent.at(-1).action,'hide');
});
test('live search retains native initial tile scope; explicit source rollback skips native adapter',async()=>{
 const h=host();await tick();h.state().search='чай';h.mutate();await tick();assert.deepEqual(JSON.parse(JSON.stringify(h.calls[1].liveScope)),[{type:'product',id:'p'}]);
 h.c.MPosNativeWorkspaceEnabled=false;h.mutate();await tick();assert.equal(h.root.style.opacity,'');
});
test('source handoff waits for nested configured add even when parent does not return its promise',async()=>{
 let finish;const h=host(null,(c,events)=>{
  c.addConfiguredCartItem=()=>new Promise(resolve=>{finish=resolve});
  c.addToCart=id=>{events.push(['add',id]);c.addConfiguredCartItem(id);};
 });await tick();
 const token=h.sent[0].token,p=h.c.__nativeWorkspaceAction({token,action:'click',key:'0'});await tick();
 await h.c.__nativeWorkspaceAction({token,action:'click',key:'0'});assert.deepEqual(h.events,[['add','p']]);
 assert.equal(h.sent.filter(packet=>packet.action==='result').length,0);finish();await p;
 assert.equal(h.sent.at(-1).action,'result');
});
test('native cart item edits explicit DTO and projects only acknowledged persisted items',async()=>{
 const inputs=[];let finish;const h=host(null,c=>{
  c.MPosCore.WorkspaceNavigation.cartItem=async input=>{
   inputs.push(input);
   if(input.operation==='cartItemView')return{ok:true,authoritative:true,model:{sessionRevision:'room-hash',id:'line'}};
   return new Promise(resolve=>{finish=resolve});
  };
 });await tick();
 await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'2'});
 const form=h.sent.find(p=>p.action==='cartItemShow');assert.ok(form);assert.deepEqual(h.events,[]);
 assert.equal(h.root.style.opacity,'0');assert.equal(h.c.MPosCore.CartItemUi.activeToken(),form.token);
 const payload={action:'cartItemSave',token:form.token,quantity:3,comment:'без сахара',discountId:'d'};
 const save=h.c.MPosCore.CartItemUi.action(payload);await tick();
 await h.c.MPosCore.CartItemUi.action(payload);h.c.MPosCore.CartItemUi.back();assert.equal(inputs.length,2);
 assert.equal(inputs[1].sessionRevision,'room-hash');assert.equal(h.state().cart.length,0);
 finish({ok:true,authoritative:true,allowed:true,items:[{cartLineId:'line',qty:3,comment:'без сахара'}]});await save;
 assert.equal(h.state().cart[0].qty,3);assert.equal(h.c.MPosCore.CartItemUi.activeToken(),null);assert.deepEqual(h.events,[['render']]);
});
test('native cart cancel does not save and stale read/recovery cannot project another cart',async()=>{
 const pending=[],h=host(null,c=>{c.MPosCore.WorkspaceNavigation.cartItem=input=>new Promise(resolve=>pending.push({input,resolve}));});await tick();
 const open=h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'2'});await tick();h.state().cart=[{cartLineId:'other'}];
 pending[0].resolve({ok:true,authoritative:true,model:{sessionRevision:'old'}});await open;
 assert.equal(h.sent.some(p=>p.action==='cartItemShow'),false);assert.equal(h.c.MPosCore.CartItemUi.activeToken(),null);
 h.flush();await tick();const next=h.c.__nativeWorkspaceAction({token:h.sent.at(-1).token,action:'click',key:'2'});await tick();
 pending[1].resolve({ok:true,authoritative:true,model:{sessionRevision:'fresh'}});await next;
 const token=h.c.MPosCore.CartItemUi.activeToken();await h.c.MPosCore.CartItemUi.action({action:'cartItemCancel',token});
 assert.equal(pending.length,2);assert.equal(h.state().cart[0].cartLineId,'other');assert.deepEqual(h.events,[]);
});
