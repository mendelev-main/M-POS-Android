const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/pos/native-workspace-navigation.js','utf8');
function host(){const calls=[],events=[];const c={state:{tab:'pos',search:'keep',cart:[{id:'p'}]},setTab:tab=>{c.state.tab=tab;events.push('legacy')},onSearch:query=>{c.state.search=query;events.push('filter')},render:()=>events.push('render'),flash:x=>events.push(x),MPosCore:{WorkspaceNavigation:{execute:input=>new Promise((resolve,reject)=>calls.push({input,resolve,reject}))}}};c.window=c;vm.createContext(c);vm.runInContext(source,c);const ready=async()=>{for(let i=0;i<8;i++)await Promise.resolve()};return{c,calls,events,ready};}
test('native tab selection applies only after initialization and correlated acknowledgement',async()=>{const h=host(),p=h.c.setTab('receipts');assert.equal(h.calls[0].input.operation,'initialize');assert.equal(h.c.state.tab,'pos');h.calls[0].resolve({snapshot:{tab:'pos',revision:1}});await h.ready();assert.equal(h.calls[1].input.operation,'selectTab');h.calls[1].resolve({snapshot:{tab:'receipts',revision:2}});assert.equal(await p,true);assert.equal(h.c.state.tab,'receipts');assert.equal(h.c.state.search,'keep');assert.equal(h.c.state.cart.length,1);assert.deepEqual(h.events,['render']);});
test('rapid tab clicks cannot apply an older native result over the latest selection',async()=>{const h=host(),first=h.c.setTab('receipts'),last=h.c.setTab('analytics');assert.equal(h.calls.length,1);h.calls[0].resolve({snapshot:{tab:'pos',revision:1}});await h.ready();h.calls[2].resolve({snapshot:{tab:'analytics',revision:3}});assert.equal(await last,true);h.calls[1].resolve({snapshot:{tab:'receipts',revision:2}});assert.equal(await first,false);assert.equal(h.c.state.tab,'analytics');assert.deepEqual(h.events,['render']);});
test('initialization failure is retryable and does not change tab or order',async()=>{const h=host(),p=h.c.setTab('receipts');h.calls[0].reject(Error('bridge unavailable'));assert.equal(await p,false);assert.equal(h.c.state.tab,'pos');const next=h.c.setTab('analytics');assert.equal(h.calls[1].input.operation,'initialize');h.calls[1].reject(Error('bridge unavailable'));await next;assert.equal(h.c.state.cart.length,1);});
test('explicit rollback and unusual nonstring input preserve reviewed setTab',async()=>{const h=host();h.c.MPosNativeWorkspaceNavigationEnabled=false;await h.c.setTab('settings');h.c.MPosNativeWorkspaceNavigationEnabled=true;await h.c.setTab(7);assert.equal(h.c.state.tab,7);assert.deepEqual(h.events,['legacy','legacy']);assert.equal(h.calls.length,0);});

test('search waits for native acknowledgement and preserves reviewed string coercion and order',async()=>{
 const h=host(),p=h.c.onSearch(0);assert.equal(h.calls[0].input.search,'keep');assert.equal(h.c.state.search,'keep');
 h.calls[0].resolve({snapshot:{tab:'pos',search:'keep',revision:1}});await h.ready();
 assert.equal(h.calls[1].input.operation,'selectFilteredSearch');assert.equal(h.calls[1].input.search,'');
 h.calls[1].resolve({snapshot:{tab:'pos',search:'',revision:2},visible:[],searchToken:'clear'});assert.equal(await p,true);
 assert.equal(h.c.state.search,'');assert.equal(h.c.state.cart.length,1);assert.deepEqual(h.events,[]);
});
test('rapid search responses cannot restore an older query',async()=>{
 const h=host(),first=h.c.onSearch('ко'),last=h.c.onSearch('кофе');h.calls[0].resolve({snapshot:{tab:'pos',search:'keep',revision:1}});await h.ready();
 h.calls[2].resolve({snapshot:{tab:'pos',search:'кофе',revision:3},visible:[],searchToken:'latest'});assert.equal(await last,true);
 h.calls[1].resolve({snapshot:{tab:'pos',search:'ко',revision:2},visible:[],searchToken:'older'});assert.equal(await first,false);
 assert.equal(h.c.state.search,'кофе');assert.deepEqual(h.events,[]);
});
test('category navigation or closing a search during native request prevents stale filtering',async()=>{
 for(const change of [c=>{c.state.posPath='Drinks';c.state.search=''},c=>{c.state.tab='receipts'},c=>{c._posFolderModal={id:'folder'}}]){
  const h=host(),p=h.c.onSearch('кофе');h.calls[0].resolve({snapshot:{tab:'pos',search:'keep',revision:1}});await h.ready();change(h.c);
  h.calls[1].resolve({snapshot:{tab:'pos',search:'кофе',revision:2},visible:[],searchToken:'stale'});assert.equal(await p,false);assert.equal(h.events.includes('filter'),false);
 }
});
test('search failure and rollback preserve existing query and reviewed filtering respectively',async()=>{
 const h=host(),p=h.c.onSearch('кофе');h.calls[0].reject(Error('bridge unavailable'));assert.equal(await p,false);assert.equal(h.c.state.search,'keep');
 h.c.MPosNativeWorkspaceNavigationEnabled=false;await h.c.onSearch('чай');assert.equal(h.c.state.search,'чай');assert.equal(h.events.at(-1),'filter');
});

test('new runtime blocks navigation until ready and drops old initialization without clearing newer promise',async()=>{
 const h=host(),old=h.c.setTab('receipts');h.c.MPosCore.WorkspaceNavigationLifecycle.invalidate();
 assert.equal(await h.c.setTab('settings'),false);assert.equal(h.calls.length,1);
 h.c.state.tab='pos';h.c.state.search='imported';h.c.MPosCore.WorkspaceNavigationLifecycle.ready();
 const current=h.c.setTab('analytics');assert.equal(h.calls.length,2);assert.equal(h.calls[1].input.search,'imported');
 h.calls[0].resolve({snapshot:{tab:'receipts',revision:2}});assert.equal(await old,false);
 h.calls[1].resolve({snapshot:{tab:'pos',revision:3}});await h.ready();assert.equal(h.calls.length,3);
 h.calls[2].resolve({snapshot:{tab:'analytics',revision:4}});assert.equal(await current,true);assert.equal(h.c.state.tab,'analytics');
});
test('late tab and search replies from before import cannot restore old UI',async()=>{
 for(const kind of ['tab','search']){const h=host(),p=kind==='tab'?h.c.setTab('receipts'):h.c.onSearch('old query');h.calls[0].resolve({snapshot:{tab:'pos',revision:1}});await h.ready();
 h.c.MPosCore.WorkspaceNavigationLifecycle.invalidate();h.c.state.tab='pos';h.c.state.search='imported';h.c.MPosCore.WorkspaceNavigationLifecycle.ready();
 h.calls[1].resolve({snapshot:{tab:'receipts',search:'old query',revision:2}});assert.equal(await p,false);assert.equal(h.c.state.tab,'pos');assert.equal(h.c.state.search,'imported');assert.deepEqual(h.events,[]);}
});

test('header read initializes owner once and rejects response from replaced runtime',async()=>{
 const h=host(),read=h.c.MPosCore.WorkspaceNavigationLifecycle.header();h.calls[0].resolve({snapshot:{tab:'pos',revision:1}});await h.ready();
 assert.equal(h.calls[1].input.operation,'headerView');assert.equal(h.calls[1].input.expected.search,'keep');
 h.c.MPosCore.WorkspaceNavigationLifecycle.invalidate();h.calls[1].resolve({navigation:{buttons:[],expected:{}}});await assert.rejects(read,/Navigation view changed/);
 await assert.rejects(h.c.MPosCore.WorkspaceNavigationLifecycle.header(),/loading/);
});

test('native search applies only typed visibility to original mounted tiles without legacy filtering or render',async()=>{
 const h=host(),tiles=[{dataset:{tileType:'product',id:'p'},hidden:false},{dataset:{tileType:'folder',id:'f'},hidden:false}];
 const grid={querySelectorAll:()=>tiles};h.c.document={getElementById:()=>grid};
 const p=h.c.onSearch('кофе');h.calls[0].resolve({snapshot:{tab:'pos',search:'keep',revision:1}});await h.ready();
 assert.deepEqual(JSON.parse(JSON.stringify(h.calls[1].input.tiles)),[{type:'product',id:'p'},{type:'folder',id:'f'}]);
 assert.equal(h.calls[1].input.expected.search,'keep');assert.equal(tiles[1].hidden,false);
 h.calls[1].resolve({snapshot:{search:'кофе',revision:2},visible:[true,false],searchToken:'search'});
 assert.equal(await p,true);assert.deepEqual(tiles.map(tile=>tile.hidden),[false,true]);
 assert.equal(h.c.state.cart.length,1);assert.deepEqual(h.events,[]);
});
test('replaced grid or changed tile ID discards accepted search without touching state or tiles',async()=>{
 for(const change of ['grid','id']){
  const h=host(),tile={dataset:{tileType:'product',id:'p'},hidden:false};let grid={querySelectorAll:()=>[tile]};
  h.c.document={getElementById:()=>grid};const p=h.c.onSearch('кофе');
  h.calls[0].resolve({snapshot:{tab:'pos',search:'keep',revision:1}});await h.ready();
  if(change==='grid')grid={querySelectorAll:()=>[tile]};else tile.dataset.id='new';
  h.calls[1].resolve({snapshot:{search:'кофе',revision:2},visible:[false],searchToken:'stale'});
  assert.equal(await p,false);assert.equal(h.c.state.search,'keep');assert.equal(tile.hidden,false);
  assert.equal(h.calls[2].input.operation,'discardSearch');assert.equal(h.calls[2].input.searchToken,'stale');
 }
});
test('malformed native mask is discarded before applying any visibility or query',async()=>{
 for(const visible of [[true,'false'],[true]]){
  const h=host(),tiles=[{dataset:{id:'a'},hidden:false},{dataset:{id:'b'},hidden:false}],grid={querySelectorAll:()=>tiles};
  h.c.document={getElementById:()=>grid};const p=h.c.onSearch('чай');
  h.calls[0].resolve({snapshot:{tab:'pos',search:'keep',revision:1}});await h.ready();
  h.calls[1].resolve({snapshot:{search:'чай',revision:2},visible,searchToken:'bad'});
  assert.equal(await p,false);assert.equal(h.c.state.search,'keep');assert.deepEqual(tiles.map(tile=>tile.hidden),[false,false]);
  assert.equal(h.calls[2].input.operation,'discardSearch');
 }
});
