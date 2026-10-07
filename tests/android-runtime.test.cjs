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
 assert.ok(scripts.includes('android-bridge.js'));assert.ok(scripts.includes('notification-native.js'));assert.ok(scripts.includes('native-settings.js'));assert.ok(scripts.includes('native-storage-shadow.js'));assert.ok(scripts.includes('native-catalog-cutover.js'));assert.ok(scripts.includes('native-network-shadow.js'));
 scripts.forEach(script=>assert.ok(fs.existsSync(path.join(assets,script)),script));
});

test('Android bridge preserves all native iPad channels',()=>{
 const bridge=fs.readFileSync(path.join(assets,'android-bridge.js'),'utf8');
 for(const channel of ['printer','telegram','photoPicker','backup','settings','storage','network','shiftScreen'])assert.match(bridge,new RegExp(`${channel}:handler\\('${channel}'\\)`));
 assert.match(bridge,/window\.__MPOS_PLATFORM__|global\.__MPOS_PLATFORM__/);
});

test('Android POS differs from source HTML only by platform scripts',()=>{
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'web-source-manifest.json')));
 const restored=html.replace('<script src="native-cash-payment.js"></script>\n','').replace('<script src="native-split-cash.js"></script>\n','').replace('<script src="native-card-confirmation.js"></script>\n','').replace('<script src="native-cart-totals.js"></script>\n','').replace('<script src="native-cart-preview.js"></script>\n','').replace('<script src="native-order-context-queue.js"></script>\n','').replace('<script src="native-delivery.js"></script>\n','').replace('<script src="native-order-settings.js"></script>\n','').replace('<script src="native-parked-command.js"></script>\n','').replace('<script src="native-catalog-edit.js"></script>\n','').replace('<script src="native-recipe-edit.js"></script>\n','').replace('<script src="native-navigation.js"></script>\n','').replace('<script src="native-employee-command.js"></script>\n','').replace('<script src="native-customer-command.js"></script>\n','').replace('<script src="native-loyalty-guard.js"></script>\n','').replace('<script src="native-loyalty-outbox.js"></script>\n','').replace('<script src="native-web-journal.js"></script>\n','').replace('<script src="native-web-sse.js"></script>\n','').replace('<script src="native-catalog-transport.js"></script>\n','').replace('<script src="native-supplier-command.js"></script>\n','').replace('<script src="native-purchase-command.js"></script>\n','').replace('<script src="native-receiving-command.js"></script>\n','').replace('<script src="native-receiving-draft.js"></script>\n','').replace('<script src="native-inventory-command.js"></script>\n','').replace('<script src="native-warehouse.js"></script>\n','').replace('<script src="native-analytics.js"></script>\n','').replace('<script src="native-hall-command.js"></script>\n','').replace('<script src="native-split-count.js"></script>\n','').replace('<script src="native-split-amount.js"></script>\n','').replace('<script src="native-split-recovery.js"></script>\n','').replace('<script src="native-payment-preflight.js"></script>\n','').replace('<script src="native-open-form.js"></script>\n','').replace('<script src="native-close-form.js"></script>\n','').replace('<script src="native-cash-forms.js"></script>\n','').replace('<script src="native-storage-warning.js"></script>\n','').replace('<script src="native-shift-screen.js"></script>\n','').replace('<script src="native-shift-reports.js"></script>\n','').replace('<script src="native-shift-lifecycle-command.js"></script>\n','').replace('<script src="native-cash-movement-command.js"></script>\n','').replace('<script src="native-return-command.js"></script>\n','').replace('<script src="native-shift-accounting.js"></script>\n','').replace('<script src="native-payment-command.js"></script>\n','').replace('<script src="native-configured-prices.js"></script>\n','').replace('<script src="native-receipts-history.js"></script>\n','').replace('<script src="android-bridge.js"></script>\n','').replace('\n<script src="native-storage-shadow.js"></script>','').replace('\n<script src="native-catalog-cutover.js"></script>','').replace('\n<script src="native-network-shadow.js"></script>','').replace('\n<script src="native-settings.js"></script>','').replace('\n<script src="notification-native.js"></script>','').replace('<script src="native-availability.js"></script>\n','');
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
 const store=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/settings/MPosSettingsStore.kt'),'utf8');
 const adapter=fs.readFileSync(path.join(assets,'native-settings.js'),'utf8');
 assert.match(activity,/MPosSettingsStore/);
 assert.match(router,/"settings" -> settings\.handle/);
 assert.match(store,/PREFS_NAME\s*=\s*"mpos_native_settings"/);
 assert.match(store,/getSharedPreferences\(PREFS_NAME,\s*Context\.MODE_PRIVATE\)/);
 assert.match(store,/platform_settings_snapshot/);
 assert.match(adapter,/__printerSettingsSnapshot/);
 assert.doesNotMatch(store,/products|orders|shifts|receipts/);
});


test('hybrid local storage makes products native while preserving other legacy domains',()=>{
 const appBuild=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const entity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/LegacyStorageShadowEntity.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
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


test('native catalog storage owns its document and preserves structured indexes',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const product=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/ProductProjectionEntity.kt'),'utf8');
 const category=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CategoryProjectionEntity.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CatalogProjectionDao.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 assert.match(db,/Migration\(1,\s*2\)/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
 assert.match(product,/tableName\s*=\s*"product_projection"/);
 assert.match(category,/tableName\s*=\s*"category_projection"/);
 assert.match(dao,/clearProducts/);
 assert.match(mirror,/"products"\s*->\s*projectCatalog\(serialized\)/);
 assert.match(mirror,/projectCatalog/);
 assert.match(fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosCatalogStorage.kt'),'utf8'),/"Без категории"/);
 assert.match(mirror,/authoritative", false/);
});


test('M POS catalog repository provides non-authoritative parity diagnostics',()=>{
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosCatalogRepository.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CatalogProjectionDao.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
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


test('native catalog primary reads retain separate projection parity diagnostics',()=>{
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosCatalogRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 const adapter=fs.readFileSync(path.join(assets,'native-storage-shadow.js'),'utf8');
 assert.match(repository,/suspend fun snapshot/);
 assert.match(repository,/catalog projection parity is not confirmed/);
 assert.match(repository,/source", "room-projection"/);
 assert.match(mirror,/"catalogSnapshot"/);
 assert.match(adapter,/mposCore\.Catalog/);
 assert.match(adapter,/nativeReadsEnabled:true/);
 assert.match(adapter,/getNativeSnapshot/);
 assert.match(adapter,/readNative\('products'\)/);
 assert.match(adapter,/domainAction\(key,'Read'\)/);
});


test('catalog authority defaults to Room under deferred comprehensive tablet acceptance',()=>{
 const controller=fs.readFileSync(path.join(assets,'native-catalog-cutover.js'),'utf8');
 assert.match(controller,/ROOM:'room'/);
 assert.match(controller,/let mode=MODES\.ROOM/);
 assert.match(controller,/activeSource:'room'/);
 assert.match(controller,/roomCutoverAllowed:true/);
 assert.match(controller,/explicit code rollback/);
});


test('employee projection uses M POS naming and explicit Room migration',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const entity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/EmployeeProjectionEntity.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/EmployeeProjectionDao.kt'),'utf8');
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosEmployeeRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 assert.match(db,/Migration\(2,\s*3\)/);
 assert.match(entity,/tableName\s*=\s*"employee_projection"/);
 assert.match(dao,/suspend fun all/);
 assert.match(repository,/class MPosEmployeeRepository/);
 assert.match(repository,/mismatchedEmployeeIds/);
 assert.match(repository,/authoritative", false/);
 assert.match(mirror,/key\) \{/);
 assert.match(mirror,/"employees" -> employeeStorage.project/);
 assert.match(mirror,/"employeeParity"/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
});


test('shift and cash movement projections use explicit Room migration and remain non-authoritative',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const shift=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/ShiftProjectionEntity.kt'),'utf8');
 const movement=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CashMovementProjectionEntity.kt'),'utf8');
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/ShiftProjectionDao.kt'),'utf8');
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosShiftRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
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
 assert.match(mirror,/"shifts"\s*->\s*shiftStorage.project\(serialized\)/);
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
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 assert.match(db,/Migration\(4,\s*5\)/);
 assert.match(order,/tableName\s*=\s*"order_projection"/);
 assert.match(line,/tableName\s*=\s*"order_line_projection"/);
 assert.match(payment,/tableName\s*=\s*"payment_projection"/);
 assert.match(line,/Index\("orderId"\)/);
 assert.match(payment,/Index\("orderId"\)/);
 assert.match(repository,/class MPosOrderRepository/);
 assert.match(repository,/mismatchedOrderIds/);
 assert.match(repository,/authoritative", false/);
 assert.match(mirror,/"orders"\s*->\s*orderStorage.project\(serialized\)/);
 assert.match(mirror,/"orderParity"/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
});


test('Room schema current version is backed by an explicit latest migration',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const version=Number(db.match(/version\s*=\s*(\d+)/)?.[1]||0);
 assert.ok(version>=1);if(version>1){assert.match(db,new RegExp('Migration\\('+String(version-1)+',\\s*'+String(version)+'\\)'));assert.match(db,new RegExp('MIGRATION_'+String(version-1)+'_'+String(version)));}
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
});

test('parked orders project through explicit Room migration and remain non-authoritative',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const order=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/ParkedOrderProjectionEntity.kt'),'utf8');
 const line=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/ParkedOrderLineProjectionEntity.kt'),'utf8');
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosParkedOrderRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 assert.match(db,/Migration\(5,\s*6\)/);
 assert.match(order,/tableName\s*=\s*"parked_order_projection"/);
 assert.match(line,/tableName\s*=\s*"parked_order_line_projection"/);
 assert.match(line,/Index\("parkedOrderId"\)/);
 assert.match(repository,/class MPosParkedOrderRepository/);
 assert.match(repository,/mismatchedParkedOrderIds/);
 assert.match(repository,/authoritative", false/);
 assert.match(mirror,/"parked"\s*->\s*parkedStorage.project\(serialized\)/);
 assert.match(mirror,/"parkedOrderParity"/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
});


test('warehouse stock events project existing receiving and inventory history without inventing a new legacy ledger',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const entity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/StockEventProjectionEntity.kt'),'utf8');
 const line=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/StockEventLineProjectionEntity.kt'),'utf8');
 const repository=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStockEventRepository.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 assert.match(db,/Migration\(6,\s*7\)/);
 assert.match(entity,/stock_event_projection/); assert.match(line,/stock_event_line_projection/);
 assert.match(repository,/"receivings"/); assert.match(repository,/"inventoryHistory"/);
 assert.match(repository,/authoritative",false/);
 assert.match(mirror,/"receivings", "inventoryHistory"/); assert.match(mirror,/"stockEventParity"/);
 assert.doesNotMatch(db,/fallbackToDestructiveMigration/);
});


test('native network transport is present but cannot take authority from legacy web orders yet',()=>{
 const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
 const transport=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/network/MPosNetworkTransport.kt'),'utf8');
 const bridge=fs.readFileSync(path.join(root,'app/src/main/assets/pos/native-network-shadow.js'),'utf8');
 const webOrders=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/web-orders.js'),'utf8');
 assert.match(gradle,/okhttp:4\.12\.0/);assert.match(transport,/class MPosNetworkTransport/);assert.match(transport,/authoritative"\s*,\s*false/);assert.match(transport,/businessHandlers","legacy"/);assert.match(bridge,/authoritative:false/);assert.match(bridge,/probe:/);assert.match(webOrders,/new EventSource/);assert.match(webOrders,/accept/);
});


test('native SSE shadow observes and reconnects without owning web-order business logic',()=>{
 const transport=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/network/MPosNetworkTransport.kt'),'utf8');
 const bridge=fs.readFileSync(path.join(root,'app/src/main/assets/pos/native-network-shadow.js'),'utf8');
 const webOrders=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/web-orders.js'),'utf8');
 assert.match(transport,/startShadowSse/);assert.match(transport,/reconnects/);assert.match(transport,/30000L/);assert.match(transport,/shadow-observed/);assert.match(transport,/authoritative"\s*,\s*false/);assert.match(bridge,/startShadowSse/);assert.match(bridge,/shadowStatus/);assert.match(webOrders,/new EventSource/);assert.doesNotMatch(transport,/PrilavokCore|parked|webOrderAcceptances/);
});


test('SSE parity diagnostics fingerprint legacy raw event data without changing business dispatch',()=>{
 const bridge=fs.readFileSync(path.join(root,'app/src/main/assets/pos/native-network-shadow.js'),'utf8');
 const webOrders=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/web-orders.js'),'utf8');
 assert.match(bridge,/observeLegacySse/);assert.match(bridge,/crypto\.subtle\.digest\('SHA-256'/);assert.match(bridge,/parityStatus/);assert.match(bridge,/authoritative:false/);
 assert.match(bridge,/NativeEventSource=global\.EventSource/);assert.match(bridge,/addEventListener\('message'/);
 assert.doesNotMatch(webOrders,/observeLegacySse/);assert.match(webOrders,/new EventSource/);
});


test('native shadow SSE follows Activity lifecycle without changing legacy EventSource authority',()=>{
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 const transport=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/network/MPosNetworkTransport.kt'),'utf8');
 const webOrders=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/web-orders.js'),'utf8');
 assert.match(activity,/nativeNetworkTransport\.onForeground\(\)/);assert.match(activity,/nativeNetworkTransport\.onBackground\(\)/);assert.match(activity,/nativeNetworkTransport\.close\(\)/);
 assert.match(transport,/background-paused/);assert.match(transport,/foreground-resumed/);assert.match(transport,/shadowRequested/);assert.match(webOrders,/new EventSource/);
});


test('shadow SSE foreground resume preserves diagnostic counters and hashes',()=>{
 const transport=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/network/MPosNetworkTransport.kt'),'utf8');
 assert.match(transport,/resetDiagnostics:Boolean=true/);assert.match(transport,/if\(resetDiagnostics\)\{shadowEvents=0; reconnects=0; lastEventHash=""\}/);assert.match(transport,/shadowDeviceKey\),false\)/);assert.match(transport,/Charsets\.UTF_8/);
});


test('P4 WEB acceptance journal is shadow-projected without taking recovery authority',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 const web=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/web-orders.js'),'utf8');
 assert.match(db,/WebAcceptanceProjectionEntity::class/);assert.match(db,/Migration\(7, 8\)/);assert.match(db,/MIGRATION_7_8/);
 assert.match(mirror,/webOrderAcceptances/);assert.match(mirror,/projectWebAcceptances/);assert.match(mirror,/pendingWebAcceptances/);
 assert.match(web,/stage:existing\?'local':'prepared'/);assert.match(web,/record\.stage='local'/);assert.match(web,/record\.stage='confirmed'/);assert.match(web,/ACK recovery is best-effort/);
});


test("P4 WEB acceptance parity compares IDs and stages without recovery authority",()=>{ const dao=fs.readFileSync(path.join(root,"app/src/main/java/com/mendelev/mpos/data/WebAcceptanceProjectionDao.kt"),"utf8"); const mirror=fs.readFileSync(path.join(root,"app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt"),"utf8"); assert.match(dao,/suspend fun all\(\)/); assert.match(mirror,/"webAcceptanceParity"/); assert.match(mirror,/missingNativeIds/); assert.match(mirror,/extraNativeIds/); assert.match(mirror,/stageMismatches/); assert.match(mirror,/authoritative", false/); });


test('P4 current order session is shadow-projected for crash recovery evidence',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 const payment=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/payment.js'),'utf8');
 assert.match(db,/CurrentOrderSessionProjectionEntity::class/);assert.match(db,/version\s*=\s*\d+/);assert.match(db,/Migration\(8, 9\)/);
 assert.match(mirror,/"currentOrderSession"/);assert.match(mirror,/recoveryStorage.project/);
 assert.match(payment,/split-payment-progress/);assert.match(payment,/currentOrderSession:currentOrderSessionSnapshot/);
});


test('P4 WEB ready transition is durable before idempotent retry',()=>{
 const web=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/web-orders.js'),'utf8');
 assert.match(web,/webOrderReadyJournal/);assert.match(web,/stage:'prepared'/);assert.match(web,/journal\[id\]\.stage='pending'/);assert.match(web,/async function recoverWebOrderReadyJournal/);assert.match(web,/async function confirmWebOrderReady/);assert.match(web,/record\.stage='confirmed'/);assert.match(web,/currentWebOrderStatus='ready'/);assert.match(web,/await saveCurrentOrderSession\(\)/);assert.match(web,/Подтверждение на сайте не отправлено/);assert.match(web,/will be repeated automatically|будет повторено автоматически/);
 assert.match(web,/addEventListener\('online',\(\)=>\{void recoverWebOrderReadyJournal\(\)/);assert.match(web,/queueMicrotask\(\(\)=>\{void recoverWebOrderReadyJournal/);
});


test('P4 loyalty recovery status is shadow-projected without native retry authority',()=>{
 const entity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/OrderProjectionEntity.kt'),'utf8');
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 const storage=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosOrderStorage.kt'),'utf8');
 const loyalty=fs.readFileSync(path.join(assets,'Web/js/features/loyalty.js'),'utf8');
 assert.match(entity,/loyaltySyncStatus/);assert.match(entity,/loyaltyReversalStatus/);assert.match(db,/version\s*=\s*\d+/);assert.match(db,/Migration\(9, 10\)/);assert.match(db,/ALTER TABLE order_projection ADD COLUMN loyaltySyncStatus/);assert.match(storage,/optJSONObject\("loyaltySync"\)/);assert.match(storage,/optJSONObject\("loyaltyReversal"\)/);
 assert.match(loyalty,/function retryPendingLoyalty/);assert.match(loyalty,/\/api\/loyalty\/sales/);assert.doesNotMatch(mirror,/api\/loyalty\/sales|retryPendingLoyalty/);
});


test('P4 WEB ready journal is shadow-projected for restart evidence',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 const entity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/WebReadyProjectionEntity.kt'),'utf8');
 assert.match(entity,/web_ready_projection/);assert.match(db,/version\s*=\s*\d+/);assert.match(db,/Migration\(10, 11\)/);assert.match(db,/WebReadyProjectionEntity::class/);assert.match(mirror,/webOrderReadyJournal/);assert.match(mirror,/projectWebReadyJournal/);assert.doesNotMatch(mirror,/\/api\/orders\/.*\/ready/);
});


test('P4 WEB ready parity compares IDs and stages without retry authority',()=>{
 const dao=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/WebReadyProjectionDao.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 assert.match(dao,/suspend fun all\(\)/);assert.match(mirror,/"webReadyParity"/);assert.match(mirror,/webOrderReadyJournal/);assert.match(mirror,/missingNativeIds/);assert.match(mirror,/extraNativeIds/);assert.match(mirror,/stageMismatches/);assert.match(mirror,/authoritative", false/);assert.doesNotMatch(mirror,/fetch\(|\/api\/orders\/.*\/ready/);
});


test('P4 WEB ready never reaches backend before local session and retry state are durable',()=>{
 const web=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/web-orders.js'),'utf8');
 const mark=web.slice(web.indexOf('async function markCurrentWebOrderReady'),web.indexOf('function testWebOrder'));
 const prepared=mark.indexOf("stage:'prepared'");const saveSession=mark.indexOf('await saveCurrentOrderSession()');const pending=mark.indexOf("journal[id].stage='pending'");const persistPending=mark.indexOf("Storage.set('webOrderReadyJournal',journal)",pending);const confirm=mark.indexOf('await confirmWebOrderReady(id,journal)');
 assert.ok(prepared>=0&&saveSession>prepared&&pending>saveSession&&persistPending>pending&&confirm>persistPending);
 assert.match(web,/record\?\.stage==='pending'&&await confirmWebOrderReady/);assert.match(web,/stage==='confirmed'\)\{delete journal\[id\];changed=true;/);
});


test('P4 critical storage journal is shadow-projected without native replay authority',()=>{
 const db=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosDatabase.kt'),'utf8');
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 const entity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/CriticalStorageJournalProjectionEntity.kt'),'utf8');
 const html=fs.readFileSync(path.join(root,'app/src/main/assets/pos/pos.html'),'utf8');
 assert.match(entity,/critical_storage_journal_projection/);assert.match(db,/version = 12/);assert.match(db,/Migration\(11, 12\)/);assert.match(db,/CriticalStorageJournalProjectionEntity::class/);assert.match(mirror,/"criticalStorageJournal"/);assert.match(mirror,/recoveryStorage.project/);assert.match(mirror,/writeKeys/);assert.match(html,/recoverCriticalStorageJournal\(\)/);assert.match(html,/commitCriticalStorage\(type,writes\)/);assert.doesNotMatch(mirror,/recoverCriticalStorageJournal|commitCriticalStorage/);
});


test('P4 critical storage journal parity is read-only and compares recovery identity',()=>{
 const mirror=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/data/MPosStorageMirror.kt'),'utf8');
 assert.match(mirror,/"criticalStorageJournalParity"/);assert.match(mirror,/criticalJournalDao\.current\(\)/);assert.match(mirror,/presenceMatches/);assert.match(mirror,/idMatches/);assert.match(mirror,/typeMatches/);assert.match(mirror,/writeKeysMatch/);assert.match(mirror,/authoritative", false/);
 const parity=mirror.slice(mirror.indexOf('"criticalStorageJournalParity"'),mirror.indexOf('"webAcceptanceParity"'));
 assert.doesNotMatch(parity,/\.set\(|\.replace\(|\.clear\(|commitCriticalStorage|recoverCriticalStorageJournal/);
});


test('P4 loyalty network retry state is persisted before send and after outcome',()=>{
 const loyalty=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/loyalty.js'),'utf8');
 const sale=loyalty.slice(loyalty.indexOf('async function publishPaidOrderLoyalty'),loyalty.indexOf('async function reverseOrderLoyalty'));
 const reversal=loyalty.slice(loyalty.indexOf('async function reverseOrderLoyalty'),loyalty.indexOf('async function settleReturnedOrderLoyalty'));
 for(const section of [sale,reversal]){const sending=section.indexOf("status:'sending'");const persist=section.indexOf("await window.PrilavokCore.Storage.set('orders',state.orders)");const network=section.indexOf("loyaltyApi(");assert.ok(sending>=0&&persist>sending&&network>persist);assert.match(section,/status:'synced'/);assert.match(section,/status:'pending'/);}
 assert.match(sale,/status:'synced'[\s\S]*await window\.PrilavokCore\.Storage\.set\('orders',state\.orders\)/);assert.match(reversal,/status:'synced'[\s\S]*await window\.PrilavokCore\.Storage\.set\('orders',state\.orders\)/);
});


test('P4 loyalty restart recovery requeues interrupted sending states',()=>{
 const loyalty=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/loyalty.js'),'utf8');
 const retry=loyalty.slice(loyalty.indexOf('function retryPendingLoyalty'),loyalty.indexOf('function loyaltyRewardAllocation'));
 assert.match(retry,/loyaltySync\?\.status==='sending'/);assert.match(retry,/loyaltyReversal\?\.status==='sending'/);assert.match(retry,/status:'pending',recoveredAt:Date\.now\(\)/);assert.match(retry,/publishPaidOrderLoyalty\(order\)/);assert.match(retry,/settleReturnedOrderLoyalty\(order\)/);
});


test('reviewed availability reference retains recovery; Android policy is installed before initialization',()=>{
 const availability=fs.readFileSync(path.join(root,'app/src/main/assets/pos/Web/js/features/availability.js'),'utf8');
 const html=fs.readFileSync(path.join(root,'app/src/main/assets/pos/pos.html'),'utf8');
 assert.match(availability,/function startAvailabilityRecovery\(\)/);assert.match(availability,/addEventListener\('online',[^\n]*publishAvailability/);assert.match(availability,/visibilitychange[^\n]*onAvailabilityAppState/);assert.match(availability,/if\(state\.loaded\)void publishAvailability\(\)/);assert.match(html,/loadAll\(\)\.then\(\(\)=>startAvailabilityRecovery\(\)\)/);
 assert.ok(html.indexOf('<script src="native-availability.js"></script>')<html.indexOf('<script>loadAll().then'));
});


test('P5 diagnostics breadcrumbs are bounded local metadata without business payloads',()=>{
 const store=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/diagnostics/MPosDiagnosticBreadcrumbStore.kt'),'utf8');
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 assert.match(store,/MAX_ENTRIES = 200/);assert.match(store,/mpos_diagnostic_breadcrumbs/);assert.match(store,/category: String/);assert.match(store,/event: String/);assert.doesNotMatch(store,/payload|customer|phone|items|deviceKey/);
 assert.match(activity,/diagnostics\.record\("printer"/);assert.match(activity,/diagnostics\.record\("network"/);assert.match(activity,/diagnostics\.record\("storage"/);
});


test('Kotlin sources do not contain escaped newline artifacts',()=>{
 const activity=fs.readFileSync(path.join(root,'app/src/main/java/com/mendelev/mpos/MainActivity.kt'),'utf8');
 assert.doesNotMatch(activity,/\\\\n/);
});
