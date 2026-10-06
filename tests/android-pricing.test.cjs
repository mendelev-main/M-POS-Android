const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const cases=JSON.parse(fs.readFileSync('tests/fixtures/pricing.json','utf8'));
const source=fs.readFileSync('app/src/main/assets/pos/Web/js/features/cart-presentation.js','utf8');
const total=fs.readFileSync('app/src/main/assets/pos/pos.html','utf8').match(/function cartTotal\(\)\{[\s\S]*?\n\}/)[0];
test('reviewed cart arithmetic matches shared Kotlin golden cases',()=>{
 for(const c of cases){const ctx={state:{cart:c.items,discounts:c.discounts,orderType:c.orderType,deliveryFee:c.deliveryFee},loyaltyRewardDiscount:()=>c.loyaltyDiscount};vm.createContext(ctx);vm.runInContext(source+'\n'+total,ctx);
 const actual={lines:c.items.map(i=>({discount:ctx.discountValue(i),total:ctx.itemTotal(i)})),subtotal:ctx.cartSubtotal(),total:ctx.cartTotal(),productDiscountTotal:Math.round(c.items.reduce((s,i)=>s+ctx.discountValue(i),0)*100)/100,subtotalBeforeDiscounts:Math.round(c.items.reduce((s,i)=>s+Number(i.price||0)*Number(i.qty||0),0)*100)/100};assert.deepEqual(actual,c.expected,c.name);}
});
