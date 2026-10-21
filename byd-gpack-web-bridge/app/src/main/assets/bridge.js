(function () {
  var state=document.getElementById('state');
  var dest=document.getElementById('destination');
  var label=document.getElementById('label');
  var detail=document.getElementById('detail');
  var invalid=document.getElementById('invalid');
  var autoButton=document.getElementById('autoToggle');
  var autoStatus=document.getElementById('autoStatus');
  var links={pin:document.getElementById('pin'),route:document.getElementById('route'),
    geo:document.getElementById('geo'),nav:document.getElementById('nav')};
  var armed=false, seenRevision=null, pending=null, going=false;
  var storage={};
  function stored(key){try{return localStorage.getItem(key);}catch(e){return storage[key]||null;}}
  function save(key,value){storage[key]=String(value);try{localStorage.setItem(key,String(value));}catch(e){}}
  armed=stored('bydWebAuto026')==='yes'||stored('bydWebAuto025')==='yes';
  var oldSeen=stored('bydWebSeen026');
  if(oldSeen!==null&&oldSeen!==''&&Number.isFinite(Number(oldSeen))){
    seenRevision=Number(oldSeen);
  }
  function good(q){
    if(!q||!q.trim())return false;
    var m=/^([-+]?\d+(?:\.\d+)?)\s*,\s*([-+]?\d+(?:\.\d+)?)$/.exec(q.trim());
    if(!m)return true; // a place name or a maps URL
    var lat=Number(m[1]), lon=Number(m[2]);
    return Number.isFinite(lat)&&Number.isFinite(lon)&&
      Math.abs(lat)<=90&&Math.abs(lon)<=180&&!(Math.abs(lat)<1e-6&&Math.abs(lon)<1e-6);
  }
  function updateButton(){
    autoButton.textContent=armed?'ปิดโหมดนำทางอัตโนมัติ':'เปิดโหมดนำทางอัตโนมัติ (ทดลอง)';
    autoButton.style.background=armed?'#16744d':'#7c3aed';
    autoStatus.textContent=armed?
      'เปิดอยู่ • เมื่อพบจุดหมายใหม่ จะทดลองเริ่มนำทางหลังกลับมาหน้านี้':
      'ปิดอยู่ • กดปุ่มสีเขียวเพื่อเริ่มนำทางได้เสมอ';
  }
  autoButton.addEventListener('click',function(){
    armed=!armed;
    save('bydWebAuto026',armed?'yes':'no');
    save('bydWebAuto025',armed?'yes':'no');
    if(!armed)pending=null;
    updateButton();
  });
  function tryLaunch(){
    if(!armed||!pending||going||document.hidden||document.visibilityState!=='visible')return;
    if(!good(pending.destination))return;
    var d=pending;
    if(stored('bydWebLaunched026')===String(d.revision)){pending=null;return;}
    pending=null;going=true;
    save('bydWebLaunched026',d.revision);
    autoStatus.textContent='กำลังลองเปิด Google Maps นำทาง…';
    window.location.assign(d.directionsUrl);
    setTimeout(function(){going=false;},4000);
  }
  function poll(){
    fetch('/api/state?ts='+Date.now(),{cache:'no-store'}).then(function(r){
      if(!r.ok)throw Error('HTTP '+r.status);return r.json();
    }).then(function(d){
      state.textContent='✓ เชื่อมต่อ Web Bridge สำเร็จ — Browser ติดต่อ Android หลักได้';
      state.className='ok';dest.textContent=d.destination;
      label.textContent=d.label+' • ลำดับข้อมูล '+d.revision;
      links.pin.href=d.searchUrl;links.route.href=d.directionsUrl;
      links.geo.href=d.geoUrl;links.nav.href=d.navigationUrl;
      detail.textContent='รับข้อมูลจาก Android หลัก • revision '+d.revision+' • '+d.feedback;
      var valid=good(d.destination);
      invalid.textContent=valid?'':'⚠ พิกัด 0,0 หรือไม่ถูกต้อง: กรุณาเปลี่ยนจุดหมายก่อนนำทาง';
      links.pin.style.pointerEvents=valid?'auto':'none';
      links.route.style.pointerEvents=valid?'auto':'none';
      links.pin.style.opacity=valid?'1':'0.45';
      links.route.style.opacity=valid?'1':'0.45';
      var rev=Number(d.revision);
      if(seenRevision===null || rev<seenRevision){
        // Initial page visit / new Bridge app state: don't launch old or sample destination.
        seenRevision=rev;
        save('bydWebSeen026',rev);
      }else if(rev!==seenRevision){
        seenRevision=rev;
        save('bydWebSeen026',rev);
        if(armed && valid && stored('bydWebLaunched026')!==String(rev)){
          pending={revision:rev,destination:d.destination,directionsUrl:d.directionsUrl};
        }
      }
      // Even if polling ran while hidden, keep the pending destination until tab is visible.
      tryLaunch();
    }).catch(function(e){
      state.textContent='× Web Bridge ติดต่อไม่ได้: '+e.message;
      state.className='bad';
      detail.textContent='เปิด Bridge V0.2.8.1 และหยุดบริการ V0.2.5 ตัวเก่า';
    });
  }
  updateButton();
  document.getElementById('retry').addEventListener('click',poll);
  document.addEventListener('visibilitychange',function(){if(!document.hidden){poll();tryLaunch();}});
  window.addEventListener('pageshow',poll);
  poll();setInterval(poll,1500);
})();