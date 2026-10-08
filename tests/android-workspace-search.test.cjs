const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const reviewed=fs.readFileSync('app/src/main/assets/pos/Web/js/features/pos-navigation.js','utf8');
const search=reviewed.slice(reviewed.indexOf('function onSearch(v){'));
const fixture=JSON.parse(fs.readFileSync('tests/fixtures/workspace-search.json','utf8'));
test('shared Kotlin search fixtures match reviewed live filter, exact IDs and mounted-tile scope',()=>{
 for(const item of fixture.cases){
  const tiles=fixture.tiles.map(tile=>({dataset:{tileType:tile.type,id:tile.id},hidden:true}));
  const context={state:{products:fixture.products,search:'old'},document:{getElementById:id=>id==='sections-wrap'?{querySelectorAll:()=>tiles}:null}};
  context.getProduct=id=>context.state.products.find(product=>product.id===id);
  vm.createContext(context);vm.runInContext(search,context);context.onSearch(item.search);
  assert.deepEqual(tiles.map(tile=>!tile.hidden),item.visible,item.search);assert.equal(context.state.search,item.search);
 }
});
