const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.join(__dirname,'../app/src/main/assets/pos');
const shared=fs.readFileSync(path.join(root,'Web/js/features/shifts.js'),'utf8');
const adapter=fs.readFileSync(path.join(root,'native-shift-accounting.js'),'utf8');
const fixtures=require('./fixtures/shift-accounting.json');
for(const fixture of fixtures)test('shift accounting: '+fixture.name,()=>{
 const state={orders:structuredClone(fixture.orders),shifts:structuredClone(fixture.shifts),company:{},currency:'₽',tab:'shift'};
 const ctx=vm.createContext({state,money:n=>String(n),fmtDate:()=>'',escapeHtml:s=>s,escapeAttr:s=>s,receiptItemDiscount:()=>0});ctx.window=ctx;
 vm.runInContext(shared,ctx);vm.runInContext(adapter,ctx);
 for(const [i,shift] of state.shifts.entries()){
  const actual=ctx.shiftTotals(shift.id),expected=fixture.expected[i];
  for(const key of ['cash','card','count','refunds'])assert.equal(actual[key],expected[key],key);
  assert.equal(ctx.cashDrawerBalance(shift),expected.balance);
  const report=ctx.buildShiftReportPayload(shift);assert.equal(report.expectedCash,expected.balance);assert.equal(report.total,expected.cash+expected.card);
  const screen=ctx.renderShiftScreen(shift);assert.ok(screen.includes(String(expected.balance)));
 }
 assert.deepEqual(state.orders,fixture.orders);assert.deepEqual(state.shifts,fixture.shifts);
});
