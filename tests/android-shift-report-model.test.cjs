const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const base=path.join(__dirname,'../app/src/main/assets/pos');
for(const fixture of require('./fixtures/shift-report-model.json'))test('report compatibility fixture: '+fixture.name,()=>{
 const ctx=vm.createContext({state:{orders:structuredClone(fixture.orders),shifts:[structuredClone(fixture.shift)],currency:fixture.currency,company:{establishmentName:fixture.establishmentName}}});ctx.window=ctx;
 vm.runInContext(fs.readFileSync(path.join(base,'Web/js/features/shifts.js'),'utf8'),ctx);vm.runInContext(fs.readFileSync(path.join(base,'native-shift-accounting.js'),'utf8'),ctx);
 assert.deepEqual(JSON.parse(JSON.stringify(ctx.buildShiftReportPayload(ctx.state.shifts[0]))),fixture.expected);
});
