const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const html=fs.readFileSync('app/src/main/assets/pos/pos.html','utf8');
const source=html.slice(html.indexOf('function productIngredients('),html.indexOf('function hasUnreturnedStockConsumption('))+'\n'+html.match(/function productTracksStock\(p\)\{[\s\S]*?\n\}/)[0];
const cases=JSON.parse(fs.readFileSync('tests/fixtures/recipe-consumption.json','utf8'));
test('reviewed recipe and stock checks match shared Kotlin fixtures without source mutations',()=>{
 for(const c of cases){const before=JSON.stringify(c);const ctx={state:{products:c.products},flash(){}};ctx.getProduct=id=>ctx.state.products.find(p=>p.id===id);vm.createContext(ctx);vm.runInContext(source,ctx);
 if(c.expected)assert.deepEqual(JSON.parse(JSON.stringify(ctx.stockConsumptionFor(c.items))),c.expected,c.name);else assert.throws(()=>ctx.stockConsumptionFor(c.items),undefined,c.name);
 if(c.valid)assert.doesNotThrow(()=>ctx.checkedStockConsumption(c.items),c.name);else assert.throws(()=>ctx.checkedStockConsumption(c.items),undefined,c.name);assert.equal(JSON.stringify(c),before);}
});
