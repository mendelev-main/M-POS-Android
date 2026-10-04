const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const vm=require('node:vm');

const root=path.resolve(__dirname,'..');
const assets=path.join(root,'app/src/main/assets/pos');
const html=fs.readFileSync(path.join(assets,'pos.html'),'utf8');
const sha=value=>crypto.createHash('sha256').update(value).digest('hex');

test('all bundled JavaScript parses',()=>{
 const files=[];const walk=directory=>fs.readdirSync(directory,{withFileTypes:true}).forEach(entry=>{const file=path.join(directory,entry.name);if(entry.isDirectory())walk(file);else if(file.endsWith('.js'))files.push(file)});walk(assets);
 files.forEach(file=>assert.doesNotThrow(()=>new vm.Script(fs.readFileSync(file,'utf8'),{filename:file}),path.relative(root,file)));
});

test('every local script referenced by POS is bundled',()=>{
 const scripts=[...html.matchAll(/<script src="([^"]+)"/g)].map(match=>match[1]);
 assert.ok(scripts.includes('android-bridge.js'));assert.ok(scripts.includes('notification-native.js'));assert.ok(scripts.includes('native-settings.js'));assert.ok(scripts.includes('native-storage-shadow.js'));assert.ok(scripts.includes('native-catalog-cutover.js'));
 scripts.forEach(script=>assert.ok(fs.existsSync(path.join(assets,script)),script));
});

test('Android bridge preserves all native iPad channels',()=>{
 const bridge=fs.readFileSync(path.join(assets,'android-bridge.js'),'utf8');
 for(const channel of ['printer','telegram','photoPicker','backup','settings','storage'])assert.match(bridge,new RegExp(`${channel}:handler\\('${channel}'\\)`));
 assert.match(bridge,/window\.__MPOS_PLATFORM__|global\.__MPOS_PLATFORM__/);
});

test('Android POS differs from source HTML only by platform scripts',()=>{
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'web-source-manifest.json')));
 const restored=html.replace('<script src="android-bridge.js"></script>\n','').replace('\n<script src="native-storage-shadow.js"></script>','').replace('\n<script src="native-catalog-cutover.js"></script>','').replace('\n<script src="native-settings.js"></script>','').replace('\n<script src="notification-native.js"></script>','');
 assert.equal(sha(restored),manifest.files['pos.html']);
 for(const [file,expected] of Object.entries(manifest.files)){
   if(file==='pos.html')continue;
   assert.equal(sha(fs.readFileSync(path.join(assets,file))),expected,file);
 }
});

test('shell uses trusted local origin and blocks file and cleartext WebView access',()=>{
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 assert.match(activity,/appassets\.androidplatform\.net/);assert.match(activity,/allowFileAccess = false/);assert.match(activity,/MIXED_CONTENT_NEVER_ALLOW/);assert.match(activity,/WEB_MESSAGE_LISTENER/);
 const manifest=fs.readFileSync(path.join(root,'app/src/main/AndroidManifest.xml'),'utf8');assert.match(manifest,/usesCleartextTraffic="false"/);
});

test('native report routes preserve shift printing and monthly Telegram delivery',()=>{
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 const reports=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/share/ReportShareManager.kt'),'utf8');
 const telegram=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/telegram/TelegramClient.kt'),'utf8');
 assert.match(activity,/"printShiftReport"[\s\S]{0,120}shares::printShiftReport/);
 assert.match(reports,/PrintManager/);
 assert.match(telegram,/"sendMonthlyWarehouseReport"/);
 assert.match(telegram,/sendDocument/);
 assert.match(activity,/onTelegramMonthlyWarehouseResult/);
});


test('native Android settings boundary is isolated from POS business storage',()=>{
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 const router=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/bridge/NativeBridgeRouter.kt'),'utf8');
 const store=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/settings/NativeSettingsStore.kt'),'utf8');
 const adapter=fs.readFileSync(path.join(assets,'native-settings.js'),'utf8');
 assert.match(activity,/NativeSettingsStore/);
 assert.match(router,/"settings" -> settings\.handle/);
 assert.match(store,/PREFS_NAME\s*=\s*"mpos_native_settings"/);
 assert.match(store,/getSharedPreferences\(PREFS_NAME,\s*Context\.MODE_PRIVATE\)/);
 assert.match(store,/platform_settings_snapshot/);
 assert.match(adapter,/__printerSettingsSnapshot/);
 assert.doesNotMatch(store,/products|orders|shifts|receipts/);
});


test('Room shadow storage preserves legacy keys without becoming authoritative',()=>{
 const appBuild=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const entity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/LegacyStorageShadowEntity.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/NativeStorageMirror.kt'),'utf8');
 const adapter=fs.readFileSync(path.join(assets,'native-storage-shadow.js'),'utf8');
 assert.match(appBuild,/androidx\.room:room-runtime/);
 assert.match(db,/@Database/);
 assert.match(entity,/tableName\s*=\s*"legacy_storage_shadow"/);
 assert.match(mirror,/"put"/);
 assert.match(adapter,/MPosCore/);
 assert.match(adapter,/mposCore\.Storage/);
 assert.match(adapter,/PrilavokCore\.Storage=mposStorage/);
 assert.match(adapter,/sourceOfTruth/);
 assert.match(adapter,/local-pos/);
 assert.doesNotMatch(adapter,/return\s+native|sourceOfTruth\s*:\s*['"]room/);
});


test('native catalog projection is structured, migrated, and non-authoritative',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const product=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/ProductProjectionEntity.kt'),'utf8');
 const category=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CategoryProjectionEntity.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CatalogProjectionDao.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/NativeStorageMirror.kt'),'utf8');
 assert.match(db,/Migration\(1,\s*2\)/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
 assert.match(product,/tableName\s*=\s*"product_projection"/);
 assert.match(category,/tableName\s*=\s*"category_projection"/);
 assert.match(dao,/clearProducts/);
 assert.match(mirror,/"products"\s*->\s*runCatching\s*\{\s*projectCatalog\(serialized\)/);
 assert.match(mirror,/projectCatalog/);
 assert.match(mirror,/"Без категории"/);
 assert.match(mirror,/authoritative", false/);
});


test('M POS catalog repository provides non-authoritative parity diagnostics',()=>{
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosCatalogRepository.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CatalogProjectionDao.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/NativeStorageMirror.kt'),'utf8');
 const adapter=fs.readFileSync(path.join(assets,'native-storage-shadow.js'),'utf8');
 assert.match(repository,/class MPosCatalogRepository/);
 assert.match(repository,/parityReport/);
 assert.match(repository,/missingProductIds/);
 assert.match(repository,/mismatchedProductIds/);
 assert.match(repository,/authoritative", false/);
 assert.match(dao,/allProducts/);
 assert.match(dao,/allCategories/);
 assert.match(mirror,/"catalogParity"/);
 assert.match(adapter,/__mposCatalogParity/);
 assert.match(adapter,/MPosCore/);
});

test('new migration rules enforce M POS naming while retaining explicit compatibility exceptions',()=>{
 const agents=fs.readFileSync(path.join(root,'AGENTS.md'),'utf8');
 const constitution=fs.readFileSync(path.join(root,'.specify/memory/constitution.md'),'utf8');
 assert.match(agents,/new or rewritten code uses M POS naming/i);
 assert.match(constitution,/M POS — единственный naming/);
 assert.match(constitution,/compatibility boundary/);
});


test('native catalog read contract is feature-gated and parity-protected',()=>{
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosCatalogRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/NativeStorageMirror.kt'),'utf8');
 const adapter=fs.readFileSync(path.join(assets,'native-storage-shadow.js'),'utf8');
 assert.match(repository,/suspend fun snapshot/);
 assert.match(repository,/catalog projection parity is not confirmed/);
 assert.match(repository,/source", "room-projection"/);
 assert.match(mirror,/"catalogSnapshot"/);
 assert.match(adapter,/mposCore\.Catalog/);
 assert.match(adapter,/nativeReadsEnabled:false/);
 assert.match(adapter,/getNativeSnapshot/);
 assert.match(adapter,/catalogSnapshot/);
});


test('catalog cutover controller defaults to compare and blocks Room activation',()=>{
 const controller=fs.readFileSync(path.join(assets,'native-catalog-cutover.js'),'utf8');
 assert.match(controller,/COMPARE:'compare'/);
 assert.match(controller,/let mode=MODES\.COMPARE/);
 assert.match(controller,/activeSource:'legacy'/);
 assert.match(controller,/roomCutoverAllowed:false/);
 assert.match(controller,/nextMode==='room'/);
 assert.match(controller,/blocked until physical acceptance/);
 assert.doesNotMatch(controller,/activeSource:'room'/);
});


test('employee projection uses M POS naming and explicit Room migration',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const entity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/EmployeeProjectionEntity.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/EmployeeProjectionDao.kt'),'utf8');
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosEmployeeRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/NativeStorageMirror.kt'),'utf8');
 assert.match(db,/Migration\(2,\s*3\)/);
 assert.match(entity,/tableName\s*=\s*"employee_projection"/);
 assert.match(dao,/suspend fun all/);
 assert.match(repository,/class MPosEmployeeRepository/);
 assert.match(repository,/mismatchedEmployeeIds/);
 assert.match(repository,/authoritative", false/);
 assert.match(mirror,/key\) \{/);
 assert.match(mirror,/"employees" -> runCatching \{ projectEmployees/);
 assert.match(mirror,/"employeeParity"/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
});


test('shift and cash movement projections use explicit Room migration and remain non-authoritative',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const shift=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/ShiftProjectionEntity.kt'),'utf8');
 const movement=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CashMovementProjectionEntity.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/ShiftProjectionDao.kt'),'utf8');
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosShiftRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/NativeStorageMirror.kt'),'utf8');
 assert.match(db,/Migration\(3,\s*4\)/);
 assert.match(shift,/tableName\s*=\s*"shift_projection"/);
 assert.match(movement,/tableName\s*=\s*"cash_movement_projection"/);
 assert.match(movement,/Index\("shiftId"\)/);
 assert.match(dao,/allShifts/);
 assert.match(dao,/allMovements/);
 assert.match(repository,/class MPosShiftRepository/);
 assert.match(repository,/mismatchedShiftIds/);
 assert.match(repository,/mismatchedMovementIds/);
 assert.match(repository,/authoritative", false/);
 assert.match(mirror,/"shifts"\s*->\s*runCatching\s*\{\s*projectShifts\(serialized\)/);
 assert.match(mirror,/"shiftParity"/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
});


test('orders, lines and payments project through explicit Room migration and remain non-authoritative',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const order=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/OrderProjectionEntity.kt'),'utf8');
 const line=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/OrderLineProjectionEntity.kt'),'utf8');
 const payment=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/PaymentProjectionEntity.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/OrderProjectionDao.kt'),'utf8');
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosOrderRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/NativeStorageMirror.kt'),'utf8');
 assert.match(db,/version\s*=\s*5/);
 assert.match(db,/Migration\(4,\s*5\)/);
 assert.match(order,/tableName\s*=\s*"order_projection"/);
 assert.match(line,/tableName\s*=\s*"order_line_projection"/);
 assert.match(payment,/tableName\s*=\s*"payment_projection"/);
 assert.match(line,/Index\("orderId"\)/);
 assert.match(payment,/Index\("orderId"\)/);
 assert.match(repository,/class MPosOrderRepository/);
 assert.match(repository,/mismatchedOrderIds/);
 assert.match(repository,/authoritative", false/);
 assert.match(mirror,/"orders"\s*->\s*runCatching\s*\{\s*projectOrders\(serialized\)/);
 assert.match(mirror,/"orderParity"/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
});
