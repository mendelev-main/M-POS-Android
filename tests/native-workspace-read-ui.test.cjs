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
test('opening native cart editor from folder closes that folder like the reviewed source form',async()=>{
 const h=host(null,(c,events)=>{
  c._posFolderModal={category:'Coffee',id:'folder'};
  c.closeModal=()=>{c._posFolderModal=null;events.push(['close']);};
  c.MPosCore.WorkspaceNavigation.cartItem=async()=>({ok:true,authoritative:true,model:{sessionRevision:'saved'}});
 });await tick();
 await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'2'});
 assert.equal(h.c._posFolderModal,null);assert.deepEqual(h.events,[['close']]);
 assert.equal(h.calls.at(-1).folderModal,null);assert.ok(h.sent.find(p=>p.action==='cartItemShow'));
 h.c.MPosCore.CartItemUi.back();assert.deepEqual(h.events,[['close']]);
});
function addHost(handler,configure){return host(null,(c,events)=>{
 c.currentOrderSessionSnapshot=()=>vm.runInContext('({items:JSON.parse(JSON.stringify(state.cart)),orderType:state.orderType,customer:{id:"customer"},updatedAt:123})',c);
 c.MPosCore.WorkspaceNavigation.cartAdd=handler;
 if(configure)configure(c,events);
});}
const addModel=(manual=false,groups=[])=>({ok:true,authoritative:true,allowed:true,supported:true,model:{manual,groups,sessionRevision:'session',catalogRevision:'catalog'}});
const added=()=>({ok:true,authoritative:true,allowed:true,items:[{cartLineId:'new',productId:'p',qty:1,price:6}],animation:{id:'new',className:'cart-item-added'}});
test('native ordinary add projects only persisted items and waits for acknowledgement',async()=>{
 const inputs=[];let finish;const h=addHost(async input=>{inputs.push(input);return input.operation==='cartAddView'?addModel():new Promise(resolve=>{finish=resolve});});await tick();
 const token=h.sent[0].token,p=h.c.__nativeWorkspaceAction({token,action:'click',key:'0'});await tick();
 assert.equal(h.state().cart.length,0);assert.equal(inputs.length,2);assert.equal(inputs[0].session.updatedAt,undefined);
 await h.c.__nativeWorkspaceAction({token,action:'click',key:'0'});assert.equal(inputs.length,2);
 finish(added());await p;assert.equal(h.state().cart[0].cartLineId,'new');assert.equal(h.c.__cartAnimation.id,'new');assert.deepEqual(h.events,[['render']]);
});
test('native manual modifier form sends explicit selection and raw price, cancel never writes',async()=>{
 const inputs=[],h=addHost(async input=>{inputs.push(input);return input.operation==='cartAddView'?addModel(true,[{id:'g'}]):added();});await tick();
 await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'0'});
 let token=h.c.MPosCore.CartAddUi.activeToken();assert.ok(h.sent.find(p=>p.action==='cartAddShow'));assert.equal(h.root.style.opacity,'0');
 await h.c.MPosCore.CartAddUi.action({action:'cartAddCancel',token});assert.equal(inputs.length,1);assert.equal(h.state().cart.length,0);
 h.flush();await tick();await h.c.__nativeWorkspaceAction({token:h.sent.filter(p=>p.action==='show').at(-1).token,action:'click',key:'0'});
 token=h.c.MPosCore.CartAddUi.activeToken();await h.c.MPosCore.CartAddUi.action({action:'cartAddSave',token,selections:[[0]],manualInput:'0,004'});
 const command=inputs.at(-1);assert.equal(command.manualInput,'0,004');assert.equal(command.catalogRevision,'catalog');assert.deepEqual(JSON.parse(JSON.stringify(command.selections)),[[0]]);
 assert.equal(h.state().cart.length,1);assert.equal(h.c.MPosCore.CartAddUi.activeToken(),null);assert.deepEqual(h.events,[['render']]);
});
test('native add rejects late views and never repeats uncertain commit through source fallback',async()=>{
 let finish;const h=addHost(()=>new Promise(resolve=>{finish=resolve}));await tick();const p=h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'0'});await tick();
 h.state().cart=[{cartLineId:'other'}];finish(addModel(true));await p;assert.equal(h.sent.some(p=>p.action==='cartAddShow'),false);assert.deepEqual(h.events,[]);
 const inputs=[],broken=addHost(async input=>{inputs.push(input);if(input.operation==='cartAddView')return addModel();throw Error('unknown commit outcome');});await tick();
 await broken.c.__nativeWorkspaceAction({token:broken.sent[0].token,action:'click',key:'0'});assert.equal(inputs.length,2);assert.equal(broken.state().cart.length,0);assert.equal(broken.events.some(e=>e[0]==='add'),false);
});
test('legacy modifier identity rollback occurs only before commit and modifier forms close folders',async()=>{
 const fallback=addHost(async()=>({ok:true,authoritative:true,supported:false}));await tick();
 await fallback.c.__nativeWorkspaceAction({token:fallback.sent[0].token,action:'click',key:'0'});assert.deepEqual(fallback.events,[['add','p']]);
 const h=addHost(async()=>addModel(false,[{id:'g'}]),(c,events)=>{c._posFolderModal={id:'folder'};c.closeModal=()=>{c._posFolderModal=null;events.push(['close']);};});await tick();
 await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'0'});assert.equal(h.c._posFolderModal,null);assert.deepEqual(h.events,[['close']]);assert.ok(h.sent.find(p=>p.action==='cartAddShow'));
 h.c.MPosCore.WorkspaceReadUi.invalidate();assert.equal(h.c.MPosCore.CartAddUi.activeToken(),null);
});
test('background session save waits for native ack and snapshots the persisted cart',async()=>{
 let finish;const saves=[],h=addHost(async input=>input.operation==='cartAddView'?addModel():new Promise(resolve=>{finish=resolve}),(c)=>{
  c.saveCurrentOrderSession=()=>saves.push(JSON.parse(vm.runInContext('JSON.stringify({items:state.cart,customer:state.customer})',c)));
 });await tick();
 const p=h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'0'});await tick();assert.equal(h.c.MPosCore.CartOperations.hasPending(),true);
 h.state().customer={id:'updated'};h.c.saveCurrentOrderSession();assert.equal(saves.length,0);
 finish(added());await p;assert.equal(saves.length,1);assert.equal(saves[0].items[0].cartLineId,'new');assert.equal(saves[0].customer.id,'updated');assert.equal(h.c.MPosCore.CartOperations.hasPending(),false);
});
test('uncertain commit blocks legacy saves and retries until a new runtime restores Room',async()=>{
 const saves=[],inputs=[],h=addHost(async input=>{inputs.push(input);if(input.operation==='cartAddView')return addModel();throw Error('lost ack');},c=>{c.saveCurrentOrderSession=()=>saves.push('saved');});await tick();
 await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'0'});h.c.saveCurrentOrderSession();
 assert.equal(h.c.MPosCore.CartOperations.hasPending(),true);assert.equal(saves.length,0);
 await h.c.__nativeWorkspaceAction({token:h.sent[0].token,action:'click',key:'0'});assert.equal(inputs.length,2);
});

function editHost(handler,configure){return host(null,(c,events)=>{
 c.currentOrderSessionSnapshot=()=>vm.runInContext('({items:JSON.parse(JSON.stringify(state.cart)),customer:state.customer})',c);
 c.changeQty=(id,delta)=>events.push(['quantity',id,delta]);c.MPosCore.WorkspaceNavigation.cartEdit=handler;
 if(configure)configure(c,events);
});}
const resetState=()=>({orderLabel:'',orderType:'На месте',deliveryFee:0,deliveryTariffSelected:false,customer:{id:'',name:'',phone:'',address:''},loyaltyPrograms:[],loyaltyRedemptions:{},_splitPayments:[],_splitCount:0,_splitPaymentTotalCents:null,loyaltyCustomerId:'',loyaltyLoadingCustomerId:'',loyaltyLoadError:'',orderComment:'',currentOrderSource:'',currentWebOrderId:'',currentWebOrderStatus:''});
test('native last removal resets context before a deferred metadata save and never invokes source remove',async()=>{
 let finish;const inputs=[],saves=[],h=editHost(input=>{inputs.push(input);return new Promise(resolve=>{finish=resolve});},c=>{
  c.saveCurrentOrderSession=()=>saves.push(JSON.parse(vm.runInContext('JSON.stringify({items:state.cart,customer:state.customer,source:state.currentOrderSource,parts:state._splitPayments})',c)));
 });await tick();h.state().cart=[{cartLineId:'line',qty:1}];h.state().customer={id:'c'};h.state().currentOrderSource='web';h.state()._splitPayments=[{amount:1}];h.c.__currentOrderKitchenPrinted=true;h.c.__currentOrderPrintedItems=[{qty:1}];h.mutate();await tick();
 const token=h.sent.filter(p=>p.action==='show').at(-1).token,p=h.c.__nativeWorkspaceAction({token,action:'click',key:'1'});await tick();assert.equal(inputs[0].operation,'cartRemoveCommit');assert.equal(h.state().cart.length,1);
 h.c.saveCurrentOrderSession();await h.c.removeFromCart('line');assert.equal(inputs.length,1);assert.equal(saves.length,0);
 finish({ok:true,authoritative:true,allowed:true,items:[],resetState:resetState()});await p;
 assert.equal(saves.length,1);assert.equal(saves[0].customer.id,'');assert.equal(saves[0].source,'');assert.equal(saves[0].parts.length,0);assert.equal(saves[0].items.length,0);assert.equal(h.c.__currentOrderKitchenPrinted,false);assert.equal(h.c.__currentOrderPrintedItems.length,0);assert.equal(h.events.some(e=>e[0]==='remove'),false);
});
test('native quantity sends explicit delta, retains empty-order context and rejects known stock without changing items',async()=>{
 const inputs=[],h=editHost(async input=>{inputs.push(input);return input.delta===1?{ok:true,authoritative:true,allowed:false,message:'stock'}:{ok:true,authoritative:true,allowed:true,items:[]};});await tick();
 h.state().cart=[{cartLineId:'line',qty:1}];h.state().customer={id:'c'};h.state().currentOrderSource='web';h.mutate();await tick();
 await h.c.changeQty('line',1);assert.equal(h.state().cart.length,1);assert.equal(h.c.MPosCore.CartOperations.hasPending(),false);
 await h.c.changeQty('line',-1);assert.equal(inputs.at(-1).operation,'cartQuantityCommit');assert.equal(inputs.at(-1).delta,-1);assert.equal(h.state().cart.length,0);assert.equal(h.state().customer.id,'c');assert.equal(h.state().currentOrderSource,'web');assert.equal(h.events.some(e=>e[0]==='quantity'),false);
});
test('uncertain remove blocks retries and source saves; late runtime ack cannot reset restored order',async()=>{
 const inputs=[],saves=[],h=editHost(async input=>{inputs.push(input);throw Error('lost ack');},c=>{c.saveCurrentOrderSession=()=>saves.push('save');});await tick();
 h.state().cart=[{cartLineId:'line'}];h.mutate();await tick();await h.c.__nativeWorkspaceAction({token:h.sent.filter(p=>p.action==='show').at(-1).token,action:'click',key:'1'});
 h.c.saveCurrentOrderSession();await h.c.removeFromCart('line');assert.equal(inputs.length,1);assert.equal(saves.length,0);assert.equal(h.state().cart.length,1);assert.equal(h.c.MPosCore.CartOperations.hasPending(),true);
 let finish;const late=editHost(()=>new Promise(resolve=>{finish=resolve}));await tick();const p=late.c.removeFromCart('line');await tick();late.c.runtime=1;late.state().customer={id:'restored'};late.state().cart=[{cartLineId:'restored'}];finish({ok:true,authoritative:true,allowed:true,items:[],resetState:resetState()});await p;assert.equal(late.state().customer.id,'restored');assert.equal(late.state().cart[0].cartLineId,'restored');
});
