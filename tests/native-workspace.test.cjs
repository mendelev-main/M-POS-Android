const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const adapter=fs.readFileSync('app/src/main/assets/pos/native-workspace.js','utf8');
function node(text='',classes=[]){return{textContent:text,style:{opacity:'',pointerEvents:'',getPropertyValue:()=> '#E4F3EE'},dataset:{},isConnected:true,disabled:false,classList:{contains:c=>classes.includes(c)},getAttribute:()=>null,querySelector(s){return this.one?.[s]??null},querySelectorAll(s){return this.many?.[s]??[]},getBoundingClientRect:()=>({left:100,top:80,width:900,height:700}),click(){this.onclick?.()},closest(){return this.parent},one:{},many:{}}}
function host(configure=()=>{}){
 const sent=[],events=[],frames=[],observers=[];let modal=null,resolve;
 const root=node('',['active']),grid=node(),panel=node(),card=node(),tile=node(),line=node(),pay=node('Оплатить'),park=node('Отложить');
 tile.dataset={tileType:'product',id:'p'};tile.one['.pcard']=card;card.one['.pcard-name']=node('Молоко');card.one['.pcard-price']=node('3,50 BYN');card.one['.pcard-stock']=node('Остаток: 10 шт');card.parent=tile;grid.many['.layout-tile']=[tile];grid.one['.empty-hint,.pos-folder-empty']=null;
 line.dataset.cartId='line-1';line.one['.cart-row-name']=node('Молоко');line.one['.cart-row-linetotal']=node('7,00 BYN');line.many['.cart-row-sub']=[node('3,50 / шт · ×2')];panel.many['.cart-row']=[line];panel.one['.cart-order-title']=node('Текущий заказ — 2 поз.');panel.one['.order-meta']=node('С собой');panel.many['.cart-head button,.order-meta button']=[];panel.many['.cart-foot button']=[park,pay];const total=node();total.one['.label']=node('Итого');total.one['.value']=node('7,00 BYN');panel.many['.total-row']=[total];
 root.one['.product-grid']=grid;root.one['.cart-panel']=panel;root.one['.zone-title-btn']=node('Рабочая зона');root.many['.pos-left .pos-toolbar button,.no-shift-banner button']=[];
 const h={state:{loaded:true,tab:'pos',editMode:false,cart:[{cartLineId:'line-1',productId:'p'}]},document:{readyState:'complete',hidden:false,body:node(),documentElement:{getAttribute:()=> 'light'},getElementById:id=>id==='screen-pos'?root:id==='app'?root:id==='modal-root'?node():null,querySelector:()=>modal,addEventListener(){}},innerWidth:1100,innerHeight:900,requestAnimationFrame:f=>frames.push(f),getComputedStyle:()=>({gridTemplateColumns:'200px 200px 200px 200px',gridColumnStart:'2',gridRowStart:'3',gridColumnEnd:'span 2',gridRowEnd:'span 1'}),addEventListener(){},MutationObserver:class{constructor(fn){observers.push(fn)}observe(){}},webkit:{messageHandlers:{workspace:{postMessage:p=>{sent.push(JSON.parse(JSON.stringify(p)));return true}}}},handlePosGridClick:e=>{events.push('tile');h.addToCart(e.target.parent.dataset.id)},handleCartRowClick:(e,id)=>events.push(['cart',id]),removeFromCart:id=>events.push(['remove',id]),cartItemKey:i=>i.cartLineId||i.productId,addToCart:id=>events.push(['add',id]),parkOrder:()=>{events.push('park');return new Promise(r=>resolve=r)},flash:s=>events.push(s)};
 park.onclick=()=>h.parkOrder();pay.onclick=()=>events.push('pay');configure(h);h.window=h;vm.createContext(h);if(h.lexicalState){const initial=h.state;delete h.state;vm.runInContext('let state = '+JSON.stringify(initial),h);}vm.runInContext(adapter,h);
 const flush=()=>{while(frames.length)frames.shift()()};const mutate=()=>{observers[0]();flush()};flush();
 return{h,sent,events,root,grid,panel,card,tile,line,pay,park,total,flush,mutate,reply:()=>resolve(),action:p=>h.__nativeWorkspaceAction({action:'click',...p}),setModal:n=>{modal=n}};
}
test('visible native model retains reviewed formatted prices/lines/totals and CSS grid spans',()=>{const h=host(),p=h.sent.at(-1);assert.equal(p.action,'show');assert.deepEqual(p.model.totals,[{label:'Итого',value:'7,00 BYN'}]);assert.equal(p.model.tiles[0].column,1);assert.equal(p.model.tiles[0].row,2);assert.equal(p.model.tiles[0].columnSpan,2);assert.equal(p.model.lines[0].details,'3,50 / шт · ×2');assert.equal(h.root.style.opacity,'0');h.mutate();assert.equal(h.sent.length,1)});
test('tile and cart routes use mounted reviewed targets; removal uses line ID and checks current cart',async()=>{const h=host(),p=h.sent[0];await h.action({token:p.token,key:p.model.tiles[0].key});await h.action({token:p.token,key:p.model.lines[0].key});await h.action({token:p.token,key:p.model.lines[0].removeKey});assert.deepEqual(h.events,['tile',['add','p'],['cart','line-1'],['remove','line-1']]);h.h.state.cart=[];await h.action({token:p.token,key:p.model.lines[0].removeKey});assert.equal(h.events.length,4)});
test('stale/detached/disabled/forged/busy/tab/modal/edit/recovery actions never dispatch',async()=>{const h=host(),p=h.sent[0],a={token:p.token,key:p.model.tiles[0].key};await h.action({...a,token:'old'});await h.action({...a,key:'addToCart()'});h.card.disabled=true;await h.action(a);h.card.disabled=false;h.card.isConnected=false;await h.action(a);h.card.isConnected=true;h.h.state.tab='settings';await h.action(a);h.h.state.tab='pos';h.h.state.editMode=true;await h.action(a);h.h.state.editMode=false;h.h.criticalStorageRecoveryPending=true;await h.action(a);h.h.criticalStorageRecoveryPending=false;h.setModal(node());await h.action(a);assert.deepEqual(h.events,[])});
test('native toolbar action waits for asynchronous reviewed commit and rejects duplicate click',async()=>{const h=host(),p=h.sent[0],key=p.model.cartButtons[0].key;const saving=h.action({token:p.token,key});await h.action({token:p.token,key});assert.deepEqual(h.events,['park']);assert.equal(h.sent.length,1);h.reply();await saving;assert.equal(h.sent.at(-1).action,'result')});
test('totals changes create a new generation; hiding for modals/edit mode restores original DOM',async()=>{const h=host(),old=h.sent[0];h.total.one['.value'].textContent='8,00 BYN';h.mutate();assert.notEqual(h.sent.at(-1).token,old.token);assert.equal(h.sent.at(-1).model.totals[0].value,'8,00 BYN');await h.action({token:old.token,key:old.model.tiles[0].key});assert.deepEqual(h.events,[]);h.h.state.editMode=true;h.mutate();assert.equal(h.sent.at(-1).action,'hide');assert.equal(h.root.style.opacity,'');assert.equal(h.root.style.pointerEvents,'')});
test('folder overlay uses native tile grid and original close, restores both roots after closing',async()=>{const h=host(),folder=node('',['pos-folder-modal']),overlay=node(),close=node('Закрыть');folder.parent=overlay;folder.one['.pos-folder-grid']=h.grid;folder.one.h2=node('Напитки');folder.one['header button']=close;h.setModal(folder);h.h._posFolderModal={id:'f'};h.mutate();const p=h.sent.at(-1);assert.equal(p.model.folder,true);assert.equal(p.model.title,'Напитки');assert.equal(overlay.style.opacity,'0');close.onclick=()=>h.setModal(null);await h.action({token:p.token,key:p.model.closeKey});h.flush();assert.equal(overlay.style.opacity,'');h.h.MPosNativeWorkspaceEnabled=false;h.mutate();assert.equal(h.root.style.opacity,'')});
test('fallback and disabled adapter keep source workspace and never introduce network/storage calls',async()=>{const h=host(),p=h.sent[0];await h.action({action:'fallback',token:p.token});assert.equal(h.h.MPosNativeWorkspaceEnabled,false);assert.equal(h.root.style.opacity,'');assert.doesNotMatch(adapter,/fetch\(|post.*catalog|eval\(|new Function/)});

test('payment page hides native workspace before the reviewed payment surface and restores it on close',async()=>{const h=host(),old=h.sent[0];h.h.state.paymentPage='main';h.mutate();assert.equal(h.sent.at(-1).action,'hide');assert.equal(h.root.style.opacity,'');await h.action({token:old.token,key:old.model.tiles[0].key});assert.deepEqual(h.events,[]);h.h.state.paymentPage='';h.mutate();assert.equal(h.sent.at(-1).action,'show')});

test('replacement of an identical mounted button rebuilds action registry without stale DOM binding',async()=>{const h=host(),old=h.sent[0],replacement=node('Оплатить');replacement.onclick=()=>h.events.push('new-pay');h.pay.isConnected=false;h.panel.many['.cart-foot button']=[h.park,replacement];h.mutate();const next=h.sent.at(-1);assert.notEqual(next.token,old.token);await h.action({token:next.token,key:next.model.cartButtons[1].key});assert.deepEqual(h.events,['new-pay'])});

test('unrecognized future DOM shape falls back to intact reviewed presentation',()=>{const h=host();h.tile.one['.pcard']=null;h.mutate();assert.equal(h.h.MPosNativeWorkspaceEnabled,false);assert.equal(h.root.style.opacity,'');assert.equal(h.sent.at(-1).action,'hide')});

const tick=()=>new Promise(r=>setImmediate(r));
test('native toolbar labels do not read HTML title; catalogue-only refresh reuses native model',async()=>{
 let reads=0;const navigation={title:'Нативная категория',buttons:[{label:'← Назад',operation:'closeCategory'}],expected:{tab:'pos',posPath:'Нативная категория',posFolder:'',search:'',editMode:false,revision:1},folderModal:null};
 const h=host(c=>{Object.assign(c.state,navigation.expected);c.MPosCore={WorkspaceNavigationLifecycle:{generation:()=>1,toolbar:async()=>{reads++;return navigation}}};});
 await tick();assert.equal(h.sent[0].model.title,'Нативная категория');assert.equal(h.sent[0].model.navigation.buttons[0].operation,'closeCategory');
 h.total.one['.value'].textContent='8,00 BYN';h.mutate();await tick();assert.equal(reads,1);assert.equal(h.sent.at(-1).model.totals[0].value,'8,00 BYN');
});
test('late toolbar read cannot cover payment or hidden application',async()=>{
 for(const change of [c=>{c.state.paymentPage='main'},c=>{c.document.hidden=true}]){
  let reply;const h=host(c=>{c.MPosCore={WorkspaceNavigationLifecycle:{generation:()=>1,toolbar:()=>new Promise(r=>reply=r)}};});
  change(h.h);reply({title:'Root',buttons:[],expected:{}});await tick();assert.equal(h.sent.length,0);assert.equal(h.root.style.opacity,'');
 }
});
test('hidden source tiles are absent from native search results',()=>{
 const h=host();h.tile.hidden=true;h.mutate();assert.equal(h.sent.at(-1).model.tiles.length,0);
 h.tile.hidden=false;h.mutate();assert.equal(h.sent.at(-1).model.tiles.length,1);
});
test('native navigation projects durable route once and discards stale acceptance',async()=>{
 const discarded=[],navigation={title:'Кофе',buttons:[],expected:{tab:'pos',posPath:'Кофе',posFolder:'',search:'',editMode:false,revision:1},folderModal:null};
 const h=host(c=>{Object.assign(c.state,navigation.expected);c.render=()=>{};c.MPosCore={WorkspaceNavigationLifecycle:{generation:()=>1,toolbar:async()=>navigation},WorkspaceNavigation:{execute:async p=>discarded.push(p)}};});
 await tick();const token=h.sent[0].token;
 await h.h.__nativeWorkspaceNavigationResult({token,result:{ok:true,patch:{posPath:null,search:'',editMode:false},effect:'render',proposalToken:'one'}});assert.equal(h.h.state.posPath,null);assert.equal(discarded.length,0);
 await h.h.__nativeWorkspaceNavigationResult({token:'stale',result:{ok:true,patch:{posPath:'Old'},effect:'render',proposalToken:'stale'}});assert.equal(h.h.state.posPath,null);assert.equal(discarded[0].operation,'discardRoute');
});
test('explicit native toolbar rollback retains reviewed title and keys',()=>{
 const h=host(c=>{c.MPosNativeWorkspaceToolbarEnabled=false;c.MPosCore={WorkspaceNavigationLifecycle:{toolbar:()=>{throw Error('must not run')}}};});assert.equal(h.sent[0].model.title,'Рабочая зона');assert.equal(h.sent[0].model.navigation,undefined);
});

test('production lexical state activates workspace without window.state and preserves payment hiding',async()=>{
 const h=host(c=>{c.lexicalState=true});assert.equal(h.h.state,undefined);
 const show=h.sent.at(-1);assert.equal(show.action,'show');assert.equal(h.root.style.opacity,'0');
 await h.action({token:show.token,key:show.model.tiles[0].key});assert.deepEqual(h.events,['tile',['add','p']]);
 vm.runInContext("state.paymentPage='main'",h.h);h.mutate();assert.equal(h.sent.at(-1).action,'hide');assert.equal(h.root.style.opacity,'');
});

test('category route metadata uses stable category ID while product and rollback keep reviewed clicks',async()=>{
 const navigation={title:'Рабочая зона',buttons:[],expected:{tab:'pos',posPath:null,posFolder:'',search:'',editMode:false,revision:1},folderModal:null};
 const h=host(c=>{Object.assign(c.state,navigation.expected);c.MPosCore={WorkspaceNavigationLifecycle:{generation:()=>1,toolbar:async()=>navigation}};});
 h.tile.dataset={tileType:'category',id:'Напитки'};h.card.one['.pcard-name'].textContent='Другое отображаемое имя';await tick();
 assert.deepEqual(h.sent.at(-1).model.tiles[0].route,{operation:'openCategory',value:'Напитки'});
 h.tile.dataset.tileType='product';h.mutate();await tick();assert.equal(h.sent.at(-1).model.tiles[0].route,undefined);
 h.h.MPosNativeWorkspaceToolbarEnabled=false;h.tile.dataset.tileType='category';h.mutate();await tick();assert.equal(h.sent.at(-1).model.tiles[0].route,undefined);
});

test('workspace carries stable tile/cart identity and preserves payment button styles across DOM replacement',()=>{
 const h=host();h.pay.classList={contains:c=>c==='btn-card'};h.park.classList={contains:c=>c==='btn-secondary'};h.mutate();const model=h.sent.at(-1).model;
 assert.equal(model.tiles[0].id,'product:p');assert.equal(model.lines[0].id,'line-1');assert.equal(model.cartButtons[0].style,'secondary');assert.equal(model.cartButtons[1].style,'card');
 h.pay.classList={contains:c=>c==='btn-cash'};h.mutate();assert.equal(h.sent.at(-1).model.cartButtons[1].style,'cash');
});

test('retained-view rollback is explicit and leaves reviewed action dispatch unchanged',()=>{const h=host();assert.equal(h.sent.at(-1).retainedUpdates,true);h.h.MPosNativeWorkspaceRetainedViewsEnabled=false;h.mutate();assert.equal(h.sent.at(-1).retainedUpdates,false)});
