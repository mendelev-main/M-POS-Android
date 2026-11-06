/* Demonstration data only; injected by scripts/web-visual-reference.py, never bundled in APK. */
(async () => {
  const params=new URLSearchParams(location.search),id=params.get('scene')||'W02';
  const scenes=await (await fetch('/manifest.json')).json();
  const scene=scenes.scenes.find(x=>x[0]===id);
  if(!scene)throw Error('Unknown reference scene');
  for(let n=0;!state.loaded && n<200;n++)await new Promise(r=>setTimeout(r,25));
  const now=Date.now(),clone=x=>JSON.parse(JSON.stringify(x));
  const products=[
    {id:'coffee',name:'Капучино',category:'Кофе',type:'composite',price:6.5,recipeYield:1,recipeUnit:'piece',tileSymbol:'☕',components:[{productId:'beans',qty:0.018},{productId:'milk',qty:0.2}],modifierGroups:[{id:'volume',name:'Добавить к напитку',min:0,max:1,options:[{id:'extra',productId:'milk',posName:'Дополнительное молоко',qty:0.05,priceDelta:0.5}]}]},
    {id:'espresso',name:'Эспрессо',category:'Кофе',type:'composite',price:4,recipeYield:1,recipeUnit:'piece',components:[{productId:'beans',qty:0.018}]},
    {id:'tea',name:'Чай с чабрецом',category:'Напитки',type:'simple',price:4.5,cost:1,stock:24,stockUnit:'piece',tileSymbol:'🍃'},
    {id:'cake',name:'Чизкейк классический',category:'Десерты',type:'simple',price:8,cost:3.5,stock:12,stockUnit:'piece',tileSymbol:'🍰'},
    {id:'water',name:'Вода без газа 0,5 л',category:'Напитки',type:'simple',price:2.5,cost:1,stock:36,stockUnit:'piece'},
    {id:'beans',name:'Кофе в зёрнах',category:'Сырьё',type:'simple',price:45,cost:24,stock:3.5,minStock:1,stockUnit:'kg',stockDisplayUnit:'g',purchaseUnit:'pack',purchasePackSize:1,purchaseContentUnit:'kg',sku:'COFFEE-01',internalNote:'Для кофемашины'},
    {id:'milk',name:'Молоко 3,2%',category:'Сырьё',type:'simple',price:3,cost:2.35,stock:8,minStock:2,stockUnit:'l',purchaseUnit:'bottle',purchasePackSize:1,purchaseContentUnit:'l'},
    {id:'manual',name:'Товар со свободной ценой',category:'Десерты',type:'simple',price:0,cost:0,stock:10,stockUnit:'piece',noStockTracking:true}
  ];
  const cart=[{cartLineId:'line1',productId:'coffee',name:'Капучино',price:6.5,basePrice:6.5,qty:2,selectedModifiers:[],comment:'Без сахара'},{cartLineId:'line2',productId:'cake',name:'Чизкейк классический',price:8,basePrice:8,qty:1,selectedModifiers:[]}];
  const employee={id:'anna',name:'Анна Иванова',phone:'',role:'employee'};
  const shift={id:'shift1',status:'open',employeeId:'anna',employeeName:employee.name,openedAt:now-7200000,openingCash:100,cashMovements:[{id:'movement',type:'deposit',amount:20,comment:'Размен',timestamp:now-3600000}]};
  const order={id:'receipt1',number:15,shiftId:'shift1',employeeId:'anna',employeeName:employee.name,timestamp:now-1800000,items:clone(cart),total:21,subtotal:21,deliveryFee:0,orderType:'На месте',method:'cash',cashGiven:25,change:4,payments:[{method:'cash',amount:21,cashGiven:25,change:4}]};
  const purchase={id:'po1',status:'pending',supplierId:'sup1',supplierName:'Кофейный поставщик',timestamp:now-86400000,requestVersion:1,items:[{productId:'beans',productName:'Кофе в зёрнах',stockUnit:'kg',requestedQty:2,requestedUnit:'pack',packSize:1,contentUnit:'kg',expectedQty:2,qty:2}]};
  const receiving={id:'rec1',timestamp:now-86400000,supplierId:'sup1',supplierName:'Кофейный поставщик',invoiceNumber:'ДЕМО-001',invoiceDate:'2026-11-06',totalCost:48,items:[{productId:'beans',productName:'Кофе в зёрнах',stockUnit:'kg',qty:2,totalCost:48,unitCost:24}]};
  const inventory={id:'inv1',type:'adhoc',startedAt:now-600000,scheduledDate:'2026-11-07',items:[{productId:'beans',name:'Кофе в зёрнах',unit:'kg',expected:3.5,actual:null,difference:0,fixedAt:null},{productId:'milk',name:'Молоко 3,2%',unit:'l',expected:8,actual:7.5,difference:-0.5,fixedAt:now-300000}]};
  Object.assign(state,{loaded:true,products:clone(products),employees:[employee,{id:'boris',name:'Борис Петров',phone:'',role:'employee'}],shifts:[shift],orders:[order],cart:clone(cart),currency:'BYN',tab:scene[3],search:'',category:'Все',posPath:null,posFolder:'',editMode:false,
    categoryOrder:['Кофе','Напитки','Десерты','Сырьё'],categoryColors:{'Кофе':'#E9DCCF','Напитки':'#DBEAFE','Десерты':'#FCE7F3','Сырьё':'#E4F3EE'},categorySymbols:{'Кофе':'☕','Напитки':'🥤','Десерты':'🍰','Сырьё':'📦'},
    layoutTiles:[{type:'category',id:'Кофе',col:1,row:1},{type:'category',id:'Напитки',col:2,row:1},{type:'category',id:'Десерты',col:3,row:1},{type:'product',id:'coffee',col:1,row:2},{type:'product',id:'cake',col:2,row:2}],
    posNavigation:{version:1,categories:[{category:'Кофе',items:[{type:'folder',id:'classic',name:'Классика'},{type:'product',id:'coffee',parentId:'classic'},{type:'product',id:'espresso'}]}]},
    parked:[{id:'park1',orderLabel:'Заказ у окна',createdAt:now-600000,items:clone(cart),cart:clone(cart),orderType:'С собой',customer:{name:'',phone:'',address:'',id:''},deliveryFee:0,total:21}],
    orderType:'На месте',orderLabel:'',customer:{name:'',phone:'',address:'',id:''},deliveryFee:0,deliveryTariffSelected:false,deliveryTariff:null,
    discounts:[{id:'discount1',name:'Скидка 10%',type:'percent',value:10}],deliveryRates:[{id:'near',name:'По городу',amount:5}],receiptLabelTemplates:[{id:'window',name:'У окна'}],
    company:{establishmentName:'M-POS · Демонстрация',legalName:'Демонстрационная организация',address:'Демо-адрес',deliveryAddress:'Демо-склад'},
    suppliers:[{id:'sup1',name:'Кофейный поставщик',productIds:['beans','milk']}],purchaseOrders:[purchase],purchaseOrderSupplierId:'sup1',purchaseOrderCart:clone(purchase.items),receivings:[receiving],receivingCart:[],receivingSupplierId:'sup1',
    inventoryConfig:{enabled:false,frequency:'monthly',productIds:['beans','milk'],lastCompletedAt:null},inventoryHistory:[],inventoryDraft:null,
    analyticsFrom:'2026-11-01',analyticsTo:'2026-11-07',analyticsSensitiveUnlocked:true,loyaltyPrograms:[],loyaltyRedemptions:{},loyaltyCustomerId:'',
    printer:{enabled:false,autoPrint:false,ip:'',port:9100},telegram:{enabled:false,botToken:'',chatId:''},network:{backendUrl:'',deviceKey:'',deviceName:'Демонстрация',lastSyncAt:null,lastSyncStatus:''},theme:params.get('theme')==='dark'?'dark':'light',_splitPayments:[],_splitCount:0,settingsAdminPanel:false});
  const admin=!['W01','W02','W03','W04','W05','C01','C02','C03','C04','C05','C06','C07','C08','C09','C10','P01','P02','P03','P04','P05','P06','P07','P08','P09','K01','K02'].includes(id);
  // Fixture authorization only. No password is copied or persisted; production code is untouched.
  administrationSession.active=()=>admin;
  applyTheme();render();
  const category=()=>{state.posPath='Кофе';render();};
  const split=()=>{renderPaymentScreen();openSplitPayment();};
  const receivingDraft=()=>{window._receivingDraft={version:1,orderId:'po1',supplierId:'sup1',invoiceNumber:'ДЕМО-002',invoiceDate:'2026-11-07',lines:[{productId:'beans',qtyInput:'2',unit:'kg',contentUnit:'kg',packSize:'',priceMode:'total',priceInput:'',totalInput:'48'}]};renderReceivingDocument();};
  const actions={
    empty(){state.cart=[];render();},cart(){},category,folder(){category();openPosFolder('classic');},search(){state.search='коф';render();},
    layoutEdit(){state.editMode=true;render();},layoutPicker(){state.editMode=true;render();openLayoutEditor();},folderEdit(){category();state.editMode=true;render();openPosFolderEditor();},tileMove(){category();state.editMode=true;render();openPosTileMove('espresso');},
    line(){openCartItemModal('line1');},modifiers(){openModifierSelection('coffee');},manualPrice(){openManualPriceModal(getProduct('manual'));},order(){openOrderSettings();},delivery(){state.orderType='Доставка';state.deliveryFee=5;state.deliveryTariffSelected=true;state.deliveryTariff=clone(state.deliveryRates[0]);state.customer={name:'Демо-покупатель',phone:'',address:'Демонстрационный адрес',id:''};render();openOrderSettings();},
    more(){document.getElementById('cart-more').open=true;},clear(){requestClearCurrentReceipt();},park(){parkOrder();},parked(){openParkedModal();},parkedDelete(){openParkedModal();requestDeleteParkedOrder('park1');},
    payment(){renderPaymentScreen();},paymentKeys(){renderPaymentScreen();openPaymentKeypad(cartTotal());},change(){renderPaymentScreen();setPaymentCash(50,cartTotal());},split,splitKeys(){split();openSplitAmountKeypad(0);},splitCash(){split();openSplitCashPayment(0);},cardConfirm(){renderPaymentScreen();openCardPartConfirmation(cartTotal(),()=>{});},splitPaid(){split();state._splitPayments[0].paid=true;state._splitPayments[0].cashGiven=20;state._splitPayments[0].change=9.5;renderSplitPayment();},paidReceipt(){showPaymentReceipt(order);},
    catalog(){},productNew(){openProductModal();},productSimple(){openProductModal('beans');},productRecipe(){openProductModal('coffee');},modifierPicker(){openProductModal('coffee');openModifierProductPicker(0,0);},productDirty(){openProductModal('beans');document.getElementById('pf-name').value='Новое название';requestCloseProductEditor();},categories(){openCategoriesModal();},categoryNew(){openCategoryModal();},categoryEdit(){openCategoryModal('Кофе');},
    shift(){},shiftClosed(){state.shifts[0].status='closed';state.shifts[0].closedAt=now;state.shifts[0].countedCash=141;render();},shiftOpen(){state.shifts[0].status='closed';state.shifts[0].closedAt=now;state.shifts[0].countedCash=141;render();openShiftModal();},deposit(){openCashMovementModal('deposit');},withdraw(){openCashMovementModal('withdraw');},shiftClose(){openCloseShiftModal();},receipts(){},receipt(){viewReceiptModal('receipt1');},refund(){openReturnConfirm('receipt1');},returned(){order.returnedAt=now;order.returnAmount=21;render();viewReceiptModal('receipt1');},analytics(){},period(){openAnalyticsPeriodModal();},
    settingsCashier(){},adminLogin(){openAdministrationSession();},settingsAdmin(){},adminPanel(){openAdminPanel();},appearance(){openAppearanceModal();},company(){openCompanyDetailsModal();},deliveryRate(){openDeliveryRateModal('near');},discount(){openDiscountModal();},receiptTemplate(){openReceiptLabelTemplateModal('window');},employeeNew(){openEmployeeModal();},employee(){openEmployeeModal('anna');},supplierNew(){openSupplierModal();},supplier(){openSupplierModal('sup1');},
    purchases(){window._purchaseHistoryExpanded=true;render();},purchaseNew(){window._purchaseExpanded=true;render();},purchaseView(){viewPurchaseOrder('po1');},purchaseEdit(){openEditPurchaseOrder('po1');},purchaseDelete(){openDeletePurchaseOrderModal('po1');},purchaseQuantity(){window._purchaseExpanded=true;render();openPurchaseQuantity('beans');},
    receivings(){window._receivingHistoryExpanded=true;render();},receiving:receivingDraft,receivingConfirm(){receivingDraft();confirmReceivingDocument();},receivingView(){viewReceivingModal('rec1');},warehouse(){openWarehousePage();},warehouseExport(){openWarehousePage();openWarehouseReportModal();},inventory(){},count(){state.inventoryDraft=clone(inventory);render();},countSummary(){state.inventoryDraft=clone(inventory);state.inventoryDraft.items[0].actual=3.4;state.inventoryDraft.items[0].difference=-0.1;state.inventoryDraft.items[0].fixedAt=now;render();showInventorySummary();},countCancel(){state.inventoryDraft=clone(inventory);render();requestCancelInventory();},backup(){openBackupManager();},backupConfirm(){prepareBackupImport({version:1,products:clone(products),employees:[employee],shifts:[shift],orders:[order],parked:[],suppliers:clone(state.suppliers),receivings:[receiving]},[]);}
  };
  try {
    await actions[scene[4]]();
    await document.fonts.ready;
    document.documentElement.dataset.referenceReady=id;
    document.documentElement.dataset.referenceTitle=scene[1];
    document.documentElement.dataset.referenceFont=document.fonts.check('15px Manrope')?'loaded':'missing';
    parent.postMessage({type:'mpos-reference-ready',id,title:scene[1]},location.origin);
  }catch(error){
    document.documentElement.dataset.referenceError=String(error);
    const note=document.createElement('pre');note.id='reference-error';note.textContent=String(error);note.style='position:fixed;inset:20px;background:#fff;color:#b91c1c;z-index:2147483647;padding:30px';document.body.append(note);
    parent.postMessage({type:'mpos-reference-error',id,error:String(error)},location.origin);
  }
})();
