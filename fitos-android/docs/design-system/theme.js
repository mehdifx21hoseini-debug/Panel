/* Shared light/dark switch for index.html + concept.html + icons.html.
   The app's default is LIGHT (owner's choice), so with no saved choice the page starts light
   instead of following the OS. The choice is remembered per browser; storage failures are ignored. */
(function(){
  var KEY='fitos-ds-theme', root=document.documentElement, t=null;
  try{ t=localStorage.getItem(KEY); }catch(e){}
  if(t!=='dark' && t!=='light') t='light';
  root.setAttribute('data-theme',t);
  function wire(){
    var sel=document.getElementById('themeSel'); if(!sel) return;
    sel.value=t;
    sel.addEventListener('change',function(){
      t=sel.value; root.setAttribute('data-theme',t);
      try{ localStorage.setItem(KEY,t); }catch(e){}
    });
  }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',wire); else wire();
})();
