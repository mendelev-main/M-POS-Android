const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const root='app/src/main/assets/pos/',source=fs.readFileSync(root+'Web/js/features/pos-navigation.js','utf8'),adapter=fs.readFileSync(root+'native-navigation.js','utf8');
const plain=v=>JSON.parse(JSON.stringify(v)),tick=()=>new Promise(r=>setImmediate(r));
function host(input,native=false){
 const calls=[],events=[],state={...structuredClone(input.state),tab:'pos',paymentPage:null,cart:[{id:'unchanged',qty:2}],products:structuredClone(input.products||[]),posNavigation:structuredClone(input.navigation||{version:1,categories:[]})};
 const ctx={state,_posFolderModal:structuredClone(input.folderModal??null),productCategoryKey:p=>String(p.category||'').trim()||'Без категории',render:()=>events.push('render'),flash:()=>{},closeModal(){ctx._posFolderModal=null;events.push('closeModal')},setTimeout:f=>{f();return 1},document:{getElementById:id=>id==='modal-root'?{firstElementChild:ctx.mountedModal??null}:null},categoryLayoutSnapshot:()=>({}),MPosCore:{NavigationRead:{},Storage:{},WorkspaceRouteRead:{calculate:input=>new Promise((resolve,reject)=>calls.push({input:plain(input),resolve,reject}))}}};ctx.window=ctx;vm.createContext(ctx);vm.runInContext(source,ctx);ctx.renderPosFolderModal=()=>events.push('renderFolder');ctx.setupLayoutGridDrag=()=>events.push('drag');if(native)vm.runInContext(adapter,ctx);return {ctx,calls,events};
}
const name={openCategory:'openPosCategory',closeCategory:'closePosCategory',openFolder:'openPosFolder',toggleEdit:'toggleEditMode'};
function view(h){return {state:plain(Object.fromEntries(['posPath','posFolder','search','editMode'].map(k=>[k,h.ctx.state[k]]))),folderModal:plain(h.ctx._posFolderModal??null),events:h.events.slice()}}
const base={posPath:null,posFolder:'',search:'чай',editMode:false};
const navigation={version:1,categories:[{category:'Кофе',items:[{type:'folder',id:'f',name:' Горячий '},{type:'folder',id:'f',name:'Duplicate'},{type:'folder',id:'bad',name:''}]}]};
const cases=[
 {name:'trim category resets search and edit',input:{version:1,operation:'openCategory',value:'\u00a0Кофе\ufeff',state:{...base,editMode:true,posFolder:'old'}}},
 {name:'blank category has no effect',input:{version:1,operation:'openCategory',value:'  ',state:base}},
 {name:'back closes folder modal first',input:{version:1,operation:'closeCategory',state:{...base,posPath:'Кофе',editMode:true},folderModal:{category:'Кофе',id:'f'}}},
 {name:'back clears legacy inline folder only',input:{version:1,operation:'closeCategory',state:{...base,posPath:'Кофе',posFolder:'f',editMode:true}}},
 {name:'back returns root',input:{version:1,operation:'closeCategory',state:{...base,posPath:'Кофе',editMode:true}}},
 {name:'edit clears search and enables drag',input:{version:1,operation:'toggleEdit',state:base}},
 {name:'leaving edit does not setup drag',input:{version:1,operation:'toggleEdit',state:{...base,editMode:true}}},
 {name:'valid folder uses normalized stored folder',input:{version:1,operation:'openFolder',value:'f',state:{...base,posPath:'Кофе'},navigation,products:[{id:'p',category:'Кофе'}]}},
 {name:'invalid folder is ignored',input:{version:1,operation:'openFolder',value:'bad',state:{...base,posPath:'Кофе'},navigation}},
 {name:'folder on root is ignored',input:{version:1,operation:'openFolder',value:'f',state:base,navigation}},
];
if(process.argv.includes('--write-fixtures')){
 const rows=cases.map(c=>{const h=host(c.input);h.ctx[name[c.input.operation]](c.input.value);return {...c,expected:view(h)}});fs.writeFileSync('tests/fixtures/workspace-route.json',JSON.stringify(rows,null,2)+'\n');
}else{
 const fixtures=JSON.parse(fs.readFileSync('tests/fixtures/workspace-route.json','utf8'));
 test('workspace transition fixtures match reviewed navigation functions',()=>{for(const c of fixtures){const h=host(c.input);h.ctx[name[c.input.operation]](c.input.value);assert.deepEqual(view(h),c.expected,c.name)}});
 const decision=c=>{const patch={};for(const k of Object.keys(c.expected.state))if(c.input.state[k]!==c.expected.state[k])patch[k]=c.expected.state[k];return {allowed:c.expected.events.length>0,patch,effect:c.expected.events[0],folderModal:c.expected.folderModal,setupDrag:c.expected.events.includes('drag')}};
 test('native transitions wait for reply and do not mutate order data',async()=>{for(const c of fixtures){const h=host(c.input,true),before=plain(h.ctx.state.cart),p=h.ctx[name[c.input.operation]](c.input.value);await tick();assert.deepEqual(h.events,[]);h.calls[0].resolve(decision(c));await p;assert.deepEqual(view(h),c.expected,c.name);assert.deepEqual(plain(h.ctx.state.cart),before);assert.equal(h.ctx.MPosCore.WorkspaceRoutes.hasPending(),false)}});
 test('stale response cannot replace a different screen, query or folder modal',async()=>{for(const change of ['tab','search','modal','dom']){const h=host(fixtures[0].input,true),p=h.ctx.openPosCategory('Кофе');await tick();if(change==='tab')h.ctx.state.tab='settings';if(change==='search')h.ctx.state.search='new query';if(change==='modal')h.ctx._posFolderModal={category:'Other',id:'new'};if(change==='dom')h.ctx.mountedModal={};h.calls[0].resolve(decision(fixtures[0]));assert.equal(await p,false);assert.deepEqual(h.events,[]);assert.equal(h.ctx.state.posPath,null)}});
 test('failed calculation or invalid effect uses reviewed read-only fallback',async()=>{for(const mode of ['error','invalid']){const h=host(fixtures[0].input,true),p=h.ctx.openPosCategory(fixtures[0].input.value);await tick();if(mode==='error')h.calls[0].reject(Error('bridge unavailable'));else h.calls[0].resolve({allowed:true,patch:{cart:[]},effect:'delete'});await p;assert.deepEqual(view(h),fixtures[0].expected);assert.equal(h.ctx.state.cart[0].qty,2)}});
 test('view transitions preserve FIFO ordering across rapid category and back',async()=>{const h=host({state:base},true),a=h.ctx.openPosCategory('Кофе'),b=h.ctx.closePosCategory();await tick();assert.equal(h.calls.length,1);h.calls[0].resolve({allowed:true,patch:{posFolder:'',posPath:'Кофе',search:'',editMode:false},effect:'render'});await a;await tick();assert.equal(h.calls.length,2);assert.equal(h.calls[1].input.state.posPath,'Кофе');h.calls[1].resolve({allowed:true,patch:{posPath:null,search:'',editMode:false},effect:'render'});await b;assert.equal(h.ctx.state.posPath,null);assert.deepEqual(h.events,['render','render'])});
 test('explicit rollback uses original function without native read',()=>{const h=host(fixtures[0].input,true);h.ctx.MPosNativeWorkspaceRouteEnabled=false;h.ctx.openPosCategory(fixtures[0].input.value);assert.deepEqual(view(h),fixtures[0].expected);assert.equal(h.calls.length,0)});
}
