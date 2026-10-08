const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-workspace.js','utf8'),tick=()=>new Promise(r=>setImmediate(r));
function host(read){const sent=[],calls=[],frames=[],events=[];let observer;
 const node=()=>({style:{opacity:'',pointerEvents:''},classList:{contains:s=>s==='active'},getBoundingClientRect:()=>({left:0,top:80,width:1200,height:700}),querySelector:s=>s==='.product-grid'?grid:panel,querySelectorAll(){throw Error('Business HTML extraction is forbidden')}}),grid=node(),panel=node(),root=node();
 const c={MPosCore:{WorkspaceNavigationLifecycle:{generation:()=>c.runtime||0,toolbar:async()=>{}},WorkspaceNavigation:{execute:async input=>{calls.push(input);return read?read(input):model(input)}}},webkit:{messageHandlers:{workspace:{postMessage:p=>{sent.push(p);return true}}}},innerWidth:1200,innerHeight:800,
 requestAnimationFrame:fn=>frames.push(fn),MutationObserver:class{constructor(fn){observer=fn}observe(){}},addEventListener(){},getComputedStyle:()=>({gridTemplateColumns:'1fr 1fr 1fr 1fr 1fr',paddingLeft:'16px',columnGap:'12px',gridTemplateRows:'155px',flexDirection:'row'}),document:{readyState:'complete',hidden:false,getElementById:id=>id==='screen-pos'?root:id==='app'?{}:null,querySelector:()=>null,body:{},documentElement:{getAttribute:()=>null},addEventListener(){}},
 addToCart:id=>events.push(['add',id]),removeFromCart:id=>events.push(['remove',id]),openCartItemModal:id=>events.push(['edit',id]),openPaymentModal:()=>events.push(['payment']),render:()=>events.push(['render']),flash:m=>events.push(['flash',m])};c.window=c;
 function model(input){return{ok:true,authoritative:true,model:{navigation:{expected:input.expected,title:'Room'},tiles:[],lines:[],catalogScope:[{type:'product',id:'p'}],actions:{0:{operation:'addProduct',value:'p'},1:{operation:'removeCartLine',value:'line'}}}};}
 vm.createContext(c);vm.runInContext("let state={loaded:true,tab:'pos',search:'',posPath:null,posFolder:'',editMode:false,paymentPage:'',cart:[],products:[],shifts:[],parked:[],currency:'BYN',orderType:'На месте',customer:{},deliveryFee:0}",c);vm.runInContext(source,c);
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
