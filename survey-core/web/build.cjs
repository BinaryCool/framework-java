const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const target=path.resolve(__dirname,'../src/main/resources/static/survey');
fs.mkdirSync(path.join(target,'vendor'),{recursive:true});
const files={
 'survey.core.min.js':'survey-core/survey.core.min.js',
 'survey-js-ui.min.js':'survey-js-ui/survey-js-ui.min.js',
 'survey.i18n.min.js':'survey-core/survey.i18n.min.js',
 'survey-core.min.css':'survey-core/survey-core.min.css'
};
const manifest={version:'3.2.1',license:'MIT',source:'https://github.com/surveyjs/survey-library',sha256:{}};
for(const [name,source] of Object.entries(files)) {
 const bytes=fs.readFileSync(path.join(__dirname,'node_modules',source));
 if(name.endsWith('.css') && bytes.length<50000) throw Error('Expected the complete SurveyJS stylesheet, not an import alias');
 fs.writeFileSync(path.join(target,'vendor',name),bytes);
 manifest.sha256[name]=crypto.createHash('sha256').update(bytes).digest('hex');
}
fs.writeFileSync(path.join(target,'vendor/manifest.json'),JSON.stringify(manifest,null,2)+'\n');
const adminIndex=process.argv.indexOf('--admin-root');
if(adminIndex>=0) {
 const admin=path.resolve(process.argv[adminIndex+1]);
 if(!fs.existsSync(path.join(admin,'src/router/index.js'))) throw Error('Expected existing Dudu admin frontend');
 fs.cpSync(target,path.join(admin,'static/survey'),{recursive:true});
}
console.log('SurveyJS 3.2.1 MIT assets packaged; no runtime CDN required.');
