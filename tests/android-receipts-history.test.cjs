const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const adapter=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/pos/native-receipts-history.js'),'utf8');
function host(){
 const calls=[],pending=[],archive=[{id:'full-archive'}];
 const context={state:{tab:'receipts',orders:archive},render(){calls.push('redraw');},
  renderReceiptsScreen(){calls.push(context.state.orders);return '<aside>'+context.state.orders.map(r=>r.id).join(',')+'</aside>';},
  MPosCore:{Receipts:{page(offset,limit){calls.push({offset,limit});return new Promise((resolve,reject)=>pending.push({resolve,reject}));}}}};
 context.window=context;vm.createContext(context);vm.runInContext(adapter,context);return {context,calls,pending,archive};
}
const flush=async()=>{for(let i=0;i<10;i++)await Promise.resolve();};
test('history queries fifty receipts and preserves complete business archive during rendering',async()=>{
 const h=host();h.context.renderReceiptsScreen();assert.deepEqual(h.calls[0],{offset:0,limit:50});
 assert.equal(h.context.state.orders,h.archive);
 h.pending.shift().resolve({rows:[{id:'page1'}],total:325});await flush();
 assert.match(h.context.renderReceiptsScreen(),/page1/);assert.equal(h.context.state.orders,h.archive);
 h.context.MPosCore.ReceiptsHistory.next();assert.deepEqual(h.calls.filter(c=>c.offset!==undefined).at(-1),{offset:50,limit:50});
});
test('invalidation ignores stale page responses and refreshes after receipt changes',async()=>{
 const h=host();h.context.renderReceiptsScreen();const old=h.pending.shift();
 h.context.MPosCore.ReceiptsHistory.invalidate();h.context.renderReceiptsScreen();
 old.resolve({rows:[{id:'stale'}],total:1});await flush();
 h.pending.shift().resolve({rows:[{id:'fresh'}],total:1});await flush();
 const html=h.context.renderReceiptsScreen();assert.match(html,/fresh/);assert.doesNotMatch(html,/stale/);
});
test('page failure shows retry and retains legacy archive presentation; retry is explicit',async()=>{
 const h=host();h.context.renderReceiptsScreen();h.pending.shift().reject(new Error('disk'));await flush();
 const html=h.context.renderReceiptsScreen();assert.match(html,/Повторить/);assert.match(html,/full-archive/);
 assert.equal(h.pending.length,0);h.context.MPosCore.ReceiptsHistory.retry();assert.equal(h.pending.length,1);
});
test('deleting final page clamps offset and inactive screen does not start native queries',async()=>{
 const h=host();h.context.state.tab='sale';h.context.renderReceiptsScreen();assert.equal(h.pending.length,0);
 h.context.state.tab='receipts';h.context.renderReceiptsScreen();h.pending.shift().resolve({rows:[{id:'first'}],total:51});await flush();
 h.context.MPosCore.ReceiptsHistory.next();h.pending.shift().resolve({rows:[],total:1});await flush();h.context.renderReceiptsScreen();
 assert.deepEqual(h.calls.filter(c=>c.offset!==undefined).at(-1),{offset:0,limit:50});
});
