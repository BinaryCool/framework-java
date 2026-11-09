(function () {
 'use strict';
 var params=new URLSearchParams(location.hash.slice(1)),ticket=params.get('ticket'),preview=params.has('preview');
 var notice=document.getElementById('notice'),retry=document.getElementById('retry'),model,busy=false,accepted=false;
 function message(text,error) { notice.textContent=text;notice.className=error?'error':''; }
 function api(method,body) {
  return fetch('/api/survey/fill',{method:method,cache:'no-store',credentials:'omit',headers:{'Authorization':'Bearer '+ticket,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined}).then(function(response){
   return response.json().then(function(data){if(!response.ok || data.code!==0)throw Error(data.msg||'问卷暂不可用');return data.data;});
  });
 }
 function render(schema) {
  if(model)model.dispose();document.getElementById('survey').innerHTML='';
  var definition=Object.assign({},schema),conditions=definition.duduConditions||{};delete definition.duduConditions;
  model=new Survey.Model(definition);
  model.getAllQuestions().forEach(function(question){if(question.getType()==='rating')question.displayMode='buttons';});
  Object.keys(conditions).forEach(function(name){model.setVariable(name,conditions[name]);});
  model.showQuestionNumbers='on';
  model.applyTheme({themeName:'default',colorPalette:'light',isPanelless:false,headerView:'basic',cssVariables:{
   '--sjs-primary-backcolor':'#4f974b','--sjs-primary-backcolor-dark':'#3d7f39','--sjs-primary-backcolor-light':'#edf5ec','--sjs-primary-forecolor':'#ffffff',
   '--sjs-general-backcolor':'#ffffff','--sjs-general-backcolor-dim':'#f6f8f6','--sjs-general-forecolor':'#243329',
   '--sjs-font-family':'"PingFang SC","Microsoft YaHei","Noto Sans SC",system-ui,-apple-system,sans-serif',
   '--sjs2-color-bg-brand-primary':'#4f974b','--sjs2-color-bg-brand-primary-hover':'#3d7f39','--sjs2-color-bg-brand-secondary':'#edf5ec',
   '--sjs2-color-fg-brand-primary':'#4f974b','--sjs2-color-fg-brand-on-primary':'#ffffff',
   '--sjs2-color-bg-basic-primary':'#ffffff','--sjs2-color-bg-basic-secondary':'#f6f8f6',
   '--sjs2-color-fg-basic-primary':'#243329','--sjs2-color-fg-basic-secondary':'#7d8980',
   '--sjs2-typography-font-family-text':'"PingFang SC","Microsoft YaHei","Noto Sans SC",system-ui,-apple-system,sans-serif'
  }});
  model.onCompleting.add(function(sender,options){if(accepted)return;options.allowComplete=false;if(preview){message('这是预览，答案不会保存。');return;}submit();});
  model.render(document.getElementById('survey'));message(preview?'预览模式 · 不会保存答案':'');
 }
 function submit() {
  if(busy || !model || accepted)return;busy=true;retry.hidden=true;message('正在提交，请稍候…');
  // Persist only in page memory. The same ticket can safely retry after a lost response.
  api('POST',{answers:model.data}).then(function(){accepted=true;model.doComplete();message('答卷已保存，可以返回问卷中心。');
   if(window.uni && window.uni.postMessage)window.uni.postMessage({data:{action:'surveyCompleted'}});
  }).catch(function(error){message(error.message||'提交失败，请重试。你的答案仍保留在当前页面。',true);retry.hidden=false;}).finally(function(){busy=false;});
 }
 retry.addEventListener('click',submit);
 window.addEventListener('beforeunload',function(event){if(model && !accepted && !preview && Object.keys(model.data).length){event.preventDefault();event.returnValue='';}});
 if(preview) {
  message('正在准备预览…');
  window.addEventListener('message',function(event){if(event.source!==window.parent || event.origin!==location.origin || !event.data || event.data.action!=='surveyPreview')return;render(event.data.schema);});
  window.parent.postMessage({action:'surveyPreviewReady'},location.origin);
 } else if(!ticket || !/^[a-f0-9]{64}$/.test(ticket))message('请从嘟嘟 App 的问卷中心进入。',true);
 else api('GET').then(function(data){render(data.schema);}).catch(function(error){message(error.message,true);});
}());
