(function () {
  var state=document.getElementById('state');
  var dest=document.getElementById('destination');
  var label=document.getElementById('label');
  var detail=document.getElementById('detail');
  var autoButton=document.getElementById('autoToggle');
  var autoStatus=document.getElementById('autoStatus');
  var links={pin:document.getElementById('pin'),route:document.getElementById('route'),
    geo:document.getElementById('geo'),nav:document.getElementById('nav')};
  var armed=false;
  var lastRevision=null, navigating=false, lastState=null;
  try{armed=localStorage.getItem('bydWebAuto025')==='yes';}catch(e){}
  function updateButton(){
    autoButton.textContent=armed?'ปิดโหมดนำทางอัตโนมัติ':'เปิดโหมดนำทางอัตโนมัติ (ทดลอง)';
    autoButton.style.background=armed?'#16744d':'#7c3aed';
    autoStatus.textContent=armed?
      'เปิดอยู่ • เมื่อข้อมูลใหม่มาถึง จะทดลองเปิดนำทางอัตโนมัติเมื่อ Browser อยู่เบื้องหน้า':
      'ปิดอยู่ • กดปุ่มสีเขียวเพื่อเปิดนำทางเองได้เสมอ';
  }
  function go(url){
    if(!url||navigating)return;
    navigating=true;
    autoStatus.textContent='กำลังเปิดเส้นทางใน Google Maps…';
    // Deliberately navigates top-level; some browsers block navigation without a tap.
    window.location.assign(url);
    setTimeout(function(){navigating=false;},4000);
  }
  autoButton.addEventListener('click',function(){
    armed=!armed;
    try{localStorage.setItem('bydWebAuto025',armed?'yes':'no');}catch(e){}
    updateButton();
    // First arming doesn't auto-open an old destination. New data will trigger.
  });
  function poll(){
    fetch('/api/state?ts='+Date.now(),{cache:'no-store'}).then(function(r){
      if(!r.ok)throw Error('HTTP '+r.status);
      return r.json();
    }).then(function(d){
      state.textContent='✓ เชื่อมต่อ Web Bridge สำเร็จ — Browser ติดต่อ Android หลักได้';
      state.className='ok';
      dest.textContent=d.destination;
      label.textContent=d.label+' • ลำดับข้อมูล '+d.revision;
      links.pin.href=d.searchUrl;links.route.href=d.directionsUrl;
      links.geo.href=d.geoUrl;links.nav.href=d.navigationUrl;
      detail.textContent='รับข้อมูลใหม่จาก Android หลัก • revision '+d.revision+' • '+d.feedback;
      if(lastRevision===null){
        // Avoid launching a previously stored destination just because page loaded.
        lastRevision=Number(d.revision);
      }else if(Number(d.revision)!==lastRevision){
        lastRevision=Number(d.revision);
        if(armed && !document.hidden && document.visibilityState==='visible'){
          // Keep page state before leaving to prevent duplicate auto navigation on return.
          go(d.directionsUrl);
        }else if(armed){autoStatus.textContent='มีจุดหมายใหม่แล้ว • กลับมาหน้านี้ หรือกดปุ่มสีเขียวเพื่อเริ่มนำทาง';}
      }
      lastState=d;
    }).catch(function(e){
      state.textContent='× Web Bridge ติดต่อไม่ได้: '+e.message;
      state.className='bad';
      detail.textContent='เปิดบริการฝั่ง Android และตรวจสอบว่าปิด V0.2.4 ตัวเก่าแล้ว';
    });
  }
  updateButton();
  document.getElementById('retry').addEventListener('click',poll);
  document.addEventListener('visibilitychange',function(){if(!document.hidden)poll();});
  poll();setInterval(poll,1500);
})();