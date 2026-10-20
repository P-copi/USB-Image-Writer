(function () {
  var state=document.getElementById('state');
  var destination=document.getElementById('destination');
  var label=document.getElementById('label');
  var detail=document.getElementById('detail');
  var links={pin:document.getElementById('pin'),route:document.getElementById('route'),
    geo:document.getElementById('geo'),nav:document.getElementById('nav')};
  function poll(){
    fetch('/api/state?_='+Date.now(),{cache:'no-store'}).then(function(resp){
      if(!resp.ok)throw Error('HTTP '+resp.status);
      return resp.json();
    }).then(function(d){
      state.textContent='✓ เชื่อมต่อ Web Bridge สำเร็จ — Browser ติดต่อ Android หลักได้';
      state.className='ok';
      destination.textContent=d.destination;
      label.textContent=d.label+' • ลำดับข้อมูล '+d.revision;
      links.pin.href=d.searchUrl;links.route.href=d.directionsUrl;
      links.geo.href=d.geoUrl;links.nav.href=d.navigationUrl;
      detail.textContent='ได้รับข้อมูลจาก Phone Space / Web Bridge แล้ว | ผลล่าสุด: '+d.feedback;
    }).catch(function(e){
      state.textContent='× เชื่อมต่อ Web Bridge ไม่สำเร็จ: '+e.message;
      state.className='bad';detail.textContent='กรุณากลับไปเปิดบริการบนแอป Web Bridge แล้วรีโหลดหน้า';
    });
  }
  document.getElementById('retry').addEventListener('click',poll);
  document.addEventListener('visibilitychange',function(){if(!document.hidden)poll();});
  poll();setInterval(poll,2500);
})();