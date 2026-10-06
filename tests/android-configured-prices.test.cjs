const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const root='app/src/main/assets/pos/';
const reference=fs.readFileSync(root+'Web/js/features/cart-composition.js','utf8'),adapter=fs.readFileSync(root+'native-configured-prices.js','utf8');
const fixtures=JSON.parse(fs.readFileSync('tests/fixtures/configured-prices.json','utf8'));
function host(native=true){
 const events=[],requests=[];let sequence=0,modal=null,stock=true;
 const products=[{id:'p',name:'Synthetic',price:10}],input={value:'10,125',focus(){events.push('focus')}};
 const ctx={MPosNativeStockPreflightEnabled:false,state:{cart:[],busy:false},criticalOperationBusy:false,getProduct:id=>products.find(p=>p.id===id),uid:()=> 'line'+(++sequence),canFulfillCart:()=>stock,saveCurrentOrderSession(){events.push('save')},closeModal(){modal=null;events.push('close')},render(){events.push('render')},flash:m=>events.push(m),document:{querySelector:()=>modal,getElementById:()=>input},openPaymentModal:()=>events.push('payment'),confirmPaymentScreen:()=>events.push('confirm'),finalizePayment:()=>events.push('finalize'),MPosCore:{ConfiguredPrices:{calculate:payload=>new Promise((resolve,reject)=>requests.push({payload,resolve,reject}))}}};
 ctx.window=ctx;vm.createContext(ctx);vm.runInContext(reference,ctx);if(native)vm.runInContext(adapter,ctx);
 return {ctx,products,input,events,requests,stock:v=>stock=v,modal:v=>modal=v,reply(index=0){const r=requests[index],i=r.payload;const raw='manualInput'in i?Number(i.manualInput.replace(',','.')):i.manualBasePrice!==null&&i.manualBasePrice!==undefined?Number(i.manualBasePrice):(Number(i.catalogPrice)||0),manual='manualInput'in i||i.manualBasePrice!==null&&i.manualBasePrice!==undefined,base='manualInput'in i?Math.round(raw*100)/100:raw;const extra=i.modifiers.reduce((s,m)=>s+Number(m.priceDelta||0),0);r.resolve({basePrice:base,price:base+extra,manualPrice:manual});}};
}
async function tickUntil(fn){for(let i=0;i<100&&!fn();i++)await Promise.resolve();assert.ok(fn());}
function cart(h){return JSON.parse(JSON.stringify(h.ctx.state.cart));}
test('reviewed native price fixtures preserve source formation and manual rounding',()=>{
 for(const c of fixtures){const h=host(false),p=h.products[0];p.price=c.input.catalogPrice;
 if('manualInput'in c.input){h.input.value=c.input.manualInput;h.ctx._manualPriceContext={productId:'p',mods:c.input.modifiers};h.ctx.confirmManualPrice();}
 else h.ctx.addConfiguredCartItem(p,c.input.modifiers,c.input.manualBasePrice??null);
 const item=h.ctx.state.cart[0],valid=!!item&&Number.isFinite(item.basePrice)&&Number.isFinite(item.price);assert.equal(valid,c.valid,c.name);
 if(valid)assert.deepEqual({basePrice:item.basePrice,extra:c.input.modifiers.reduce((s,m)=>s+Number(m.priceDelta||0),0),price:item.price,manualPrice:item.manualPrice},c.expected,c.name);}
});
test('queued taps match original merging and payment waits for native price; qty is not a delta multiplier',async()=>{
 const h=host(),legacy=host(false),mods=[{groupId:'g',productId:'m',priceDelta:2,qty:3}];
 const a=h.ctx.addConfiguredCartItem(h.products[0],mods),b=h.ctx.addConfiguredCartItem(h.products[0],mods);
 assert.equal(h.ctx.state.cart.length,0);h.ctx.openPaymentModal();h.ctx.finalizePayment();assert.ok(!h.events.includes('payment'));assert.ok(!h.events.includes('finalize'));
 await tickUntil(()=>h.requests.length===1);h.reply();await a;await tickUntil(()=>h.requests.length===2);h.reply(1);await b;
 legacy.ctx.addConfiguredCartItem(legacy.products[0],mods);legacy.ctx.addConfiguredCartItem(legacy.products[0],mods);
 assert.deepEqual(cart(h),cart(legacy));assert.equal(cart(h)[0].price,12);assert.equal(cart(h)[0].qty,2);h.ctx.openPaymentModal();assert.equal(h.events.at(-1),'payment');
});
test('manual entry passes raw text to Kotlin, stays separate and validates before rounding',async()=>{
 const h=host(),legacy=host(false);const context={productId:'p',mods:[{priceDelta:2.345,qty:2}]};h.ctx._manualPriceContext=context;legacy.ctx._manualPriceContext=structuredClone(context);
 const a=h.ctx.confirmManualPrice(),duplicate=h.ctx.confirmManualPrice();await tickUntil(()=>h.requests.length===1);assert.equal(h.requests[0].payload.manualInput,'10,125');h.reply();await a;await duplicate;legacy.ctx.confirmManualPrice();assert.deepEqual(cart(h),cart(legacy));assert.equal(h.requests.length,1);assert.equal(h.ctx._manualPriceContext,null);
 h.input.value='0';h.ctx._manualPriceContext=context;h.ctx.confirmManualPrice();assert.equal(h.events.at(-1),'focus');assert.equal(h.requests.length,1);
});
test('source historical price merging, discount/comment exclusions and manual-row interaction remain',async()=>{
 const h=host(),legacy=host(false);
 const seed=[{cartLineId:'old',productId:'p',price:7,basePrice:7,manualPrice:true,qty:1,selectedModifiers:[]}];h.ctx.state.cart=structuredClone(seed);legacy.ctx.state.cart=structuredClone(seed);
 const first=h.ctx.addConfiguredCartItem(h.products[0],[]);await tickUntil(()=>h.requests.length===1);h.reply();await first;legacy.ctx.addConfiguredCartItem(legacy.products[0],[]);assert.deepEqual(cart(h),cart(legacy));assert.equal(cart(h)[0].price,7);
 h.ctx.state.cart[0].discountId='sale';legacy.ctx.state.cart[0].discountId='sale';const second=h.ctx.addConfiguredCartItem(h.products[0],[]);await tickUntil(()=>h.requests.length===2);h.reply(1);await second;legacy.ctx.addConfiguredCartItem(legacy.products[0],[]);assert.deepEqual(cart(h),cart(legacy));
});
test('late results after session change, modal cancel, manual cancel or payment cannot alter cart',async()=>{
 for(const change of [h=>h.ctx.state.cart=[],h=>h.modal(null),h=>h.ctx._manualPriceContext=null,h=>h.ctx.criticalOperationBusy=true]){
  const h=host();h.modal({});h.ctx._manualPriceContext={productId:'p',mods:[]};const work=h.ctx.confirmManualPrice();await tickUntil(()=>h.requests.length===1);change(h);h.reply();await work;assert.equal(cart(h).length,0);assert.ok(!h.events.includes('save'));
 }
});
test('stock refusal, native error and changed product never save a priced line; next request can recover',async()=>{
 for(const mode of ['stock','error','changed']){const h=host();const work=h.ctx.addConfiguredCartItem(h.products[0],[]);await tickUntil(()=>h.requests.length===1);
  if(mode==='stock')h.stock(false);if(mode==='changed')h.products[0].price=11;if(mode==='error')h.requests[0].reject(new Error('read failed'));else h.reply();await work;assert.equal(cart(h).length,0);assert.ok(!h.events.includes('save'));
  h.stock(true);const next=h.ctx.addConfiguredCartItem(h.products[0],[]);await tickUntil(()=>h.requests.length===2);h.reply(1);await next;assert.equal(cart(h).length,1);
 }
});
test('explicit rollback uses original synchronous add and manual forms; adapter follows payment runtime',()=>{
 const h=host();h.ctx.MPosNativeConfiguredPricesEnabled=false;h.ctx.addConfiguredCartItem(h.products[0],[]);assert.equal(cart(h).length,1);assert.equal(h.requests.length,0);
 const html=fs.readFileSync(root+'pos.html','utf8');assert.ok(html.indexOf('src="native-configured-prices.js"')>html.indexOf('src="native-payment-command.js"'));
});
function withNativeStock(){const h=host(),checks=[];h.ctx.MPosNativeStockPreflightEnabled=true;h.ctx.state.products=h.products;h.ctx.MPosCore.StockPreflight={check:input=>new Promise((resolve,reject)=>checks.push({input,resolve,reject}))};return {...h,checks};}
test('native stock verdict gates additions and preserves FIFO merging without JS fallback',async()=>{
 const h=withNativeStock();h.ctx.canFulfillCart=()=>{throw new Error('legacy stock check must not run')};
 const a=h.ctx.addConfiguredCartItem(h.products[0],[]),b=h.ctx.addConfiguredCartItem(h.products[0],[]);
 await tickUntil(()=>h.requests.length===1);h.reply();await tickUntil(()=>h.checks.length===1);assert.equal(cart(h).length,0);h.ctx.openPaymentModal();assert.ok(!h.events.includes('payment'));assert.equal(h.checks[0].input.items[0].qty,1);
 h.checks[0].resolve({allowed:true});await a;await tickUntil(()=>h.requests.length===2);h.reply(1);await tickUntil(()=>h.checks.length===2);assert.equal(h.checks[1].input.items[0].qty,2);h.checks[1].resolve({allowed:false,reason:'Недостаточно остатка: Synthetic'});await b;assert.equal(cart(h)[0].qty,1);assert.equal(h.events.at(-1),'Недостаточно остатка: Synthetic');
});
test('modifier quantities and manual raw input reach native stock check without receipt or price data',async()=>{
 const h=withNativeStock();h.ctx._manualPriceContext={productId:'p',mods:[{productId:'m',qty:3,priceDelta:2}]};const a=h.ctx.confirmManualPrice();await tickUntil(()=>h.requests.length===1);h.reply();await tickUntil(()=>h.checks.length===1);
 const proposal=JSON.parse(JSON.stringify(h.checks[0].input));assert.deepEqual(proposal.items,[{productId:'p',qty:1,selectedModifiers:[{productId:'m',qty:3}]}]);assert.ok(h.ctx._manualPriceContext);h.checks[0].resolve({allowed:true});await a;assert.equal(cart(h).length,1);assert.equal(h.ctx._manualPriceContext,null);
});
test('quantity, recipe, stock, session or modal changes invalidate late native stock approval',async()=>{
 for(const change of [h=>h.ctx.state.cart[0].qty++,h=>h.products[0].stock=0,h=>h.products[0].components=[{productId:'changed',qty:1}],h=>h.ctx.state.cart=[],h=>h.modal(null)]){
  const h=withNativeStock();h.ctx.state.cart=[{productId:'p',qty:1,price:10}];h.modal({});const a=h.ctx.addConfiguredCartItem(h.products[0],[]);await tickUntil(()=>h.requests.length===1);h.reply();await tickUntil(()=>h.checks.length===1);change(h);const before=JSON.stringify(h.ctx.state.cart);h.checks[0].resolve({allowed:true});await a;assert.equal(JSON.stringify(h.ctx.state.cart),before);assert.ok(!h.events.includes('save'));
 }
});
test('failed stock read leaves cart and manual context intact and a later retry succeeds',async()=>{
 const h=withNativeStock();h.ctx._manualPriceContext={productId:'p',mods:[]};const a=h.ctx.confirmManualPrice();await tickUntil(()=>h.requests.length===1);h.reply();await tickUntil(()=>h.checks.length===1);h.checks[0].reject(new Error('read unavailable'));await a;assert.equal(cart(h).length,0);assert.ok(h.ctx._manualPriceContext);assert.equal(h.events.at(-1),'Не удалось проверить остатки. Повторите добавление.');
 const b=h.ctx.confirmManualPrice();await tickUntil(()=>h.requests.length===2);h.reply(1);await tickUntil(()=>h.checks.length===2);h.checks[1].resolve({allowed:true});await b;assert.equal(cart(h).length,1);
});
function withNativeQuantity(){const h=withNativeStock(),quantities=[];h.ctx.MPosCore.CartQuantity={check:input=>new Promise((resolve,reject)=>quantities.push({input,resolve,reject}))};h.ctx.state.cart=[{cartLineId:'l',productId:'p',qty:1,price:10}];return {...h,quantities,qReply(index=0,allowed=true){const r=quantities[index],q=r.input.items[r.input.targetIndex].qty+r.input.delta;r.resolve({quantity:q,remove:q<=0,allowed,reason:'shortage'});}};}
test('quantity taps share FIFO with additions and payment waits; complete cart matches reviewed stepper',async()=>{
 const h=withNativeQuantity(),legacy=host(false);legacy.ctx.state.cart=structuredClone(cart(h));
 const a=h.ctx.changeQty('l',1),b=h.ctx.changeQty('l',-1);await tickUntil(()=>h.quantities.length===1);h.ctx.finalizePayment();assert.ok(!h.events.includes('finalize'));h.qReply();await a;await tickUntil(()=>h.quantities.length===2);assert.equal(h.quantities[1].input.items[0].qty,2);h.qReply(1);await b;
 legacy.ctx.changeQty('l',1);legacy.ctx.changeQty('l',-1);assert.deepEqual(cart(h),cart(legacy));
 const adding=h.ctx.addConfiguredCartItem(h.products[0],[]),increase=h.ctx.changeQty('l',1);await tickUntil(()=>h.requests.length===1);h.reply();await tickUntil(()=>h.checks.length===1);h.checks[0].resolve({allowed:true});await adding;await tickUntil(()=>h.quantities.length===3);assert.equal(h.quantities[2].input.items[0].qty,2);h.qReply(2);await increase;assert.equal(cart(h)[0].qty,3);
});
test('native quantity refusal and read failure leave row untouched and allow subsequent retry',async()=>{
 const h=withNativeQuantity();const a=h.ctx.changeQty('l',1);await tickUntil(()=>h.quantities.length===1);h.qReply(0,false);await a;assert.equal(cart(h)[0].qty,1);assert.ok(!h.events.includes('save'));
 const b=h.ctx.changeQty('l',1);await tickUntil(()=>h.quantities.length===2);h.quantities[1].reject(new Error('offline native read'));await b;assert.equal(cart(h)[0].qty,1);
 const c=h.ctx.changeQty('l',1);await tickUntil(()=>h.quantities.length===3);h.qReply(2);await c;assert.equal(cart(h)[0].qty,2);
});
test('removing last row through quantity preserves source order context and cancels queued stale edits',async()=>{
 const h=withNativeQuantity();Object.assign(h.ctx.state,{orderLabel:'Synthetic',orderType:'Доставка',deliveryFee:2,customer:{id:'synthetic'}});const a=h.ctx.changeQty('l',-1),b=h.ctx.changeQty('l',1);await tickUntil(()=>h.quantities.length===1);h.qReply();await a;await b;assert.equal(cart(h).length,0);assert.equal(h.quantities.length,1);assert.equal(h.ctx.state.orderLabel,'Synthetic');assert.equal(h.ctx.state.customer.id,'synthetic');assert.equal(h.ctx.state.deliveryFee,2);
});
test('external removal, quantity or stock change cancels late quantity replies without resurrecting rows',async()=>{
 for(const change of [h=>h.ctx.state.cart=[],h=>h.ctx.state.cart[0].qty=5,h=>h.products[0].stock=0]){const h=withNativeQuantity();const a=h.ctx.changeQty('l',1);await tickUntil(()=>h.quantities.length===1);change(h);const before=JSON.stringify(h.ctx.state.cart);h.qReply();await a;assert.equal(JSON.stringify(h.ctx.state.cart),before);assert.ok(!h.events.includes('save'));}
});
test('duplicate legacy keys and string quantity preserve original proposal semantics; rollback is synchronous',async()=>{
 const h=withNativeQuantity();h.ctx.state.cart=[{productId:'p',qty:'1',price:10},{productId:'p',qty:1,price:10}];const a=h.ctx.changeQty('p',1);await tickUntil(()=>h.quantities.length===1);assert.deepEqual(JSON.parse(JSON.stringify(h.quantities[0].input.matchingIndices)),[0,1]);h.qReply();await a;assert.equal(h.ctx.state.cart[0].qty,'11');assert.equal(h.ctx.state.cart[1].qty,1);
 const r=withNativeQuantity();r.ctx.MPosNativeCartQuantityEnabled=false;r.ctx.changeQty('l',1);assert.equal(cart(r)[0].qty,2);assert.equal(r.quantities.length,0);
});
test('stepper removal keeps same order lineage so a later queued product tap still adds',async()=>{
 const h=withNativeQuantity();const remove=h.ctx.changeQty('l',-1),add=h.ctx.addConfiguredCartItem(h.products[0],[]);await tickUntil(()=>h.quantities.length===1);h.qReply();await remove;await tickUntil(()=>h.requests.length===1);h.reply();await tickUntil(()=>h.checks.length===1);assert.equal(h.checks[0].input.items.length,1);assert.equal(h.checks[0].input.items[0].qty,1);h.checks[0].resolve({allowed:true});await add;assert.equal(cart(h).length,1);assert.equal(cart(h)[0].qty,1);
});
