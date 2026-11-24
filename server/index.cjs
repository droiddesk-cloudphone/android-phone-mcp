'use strict';
const https=require('node:https');const fs=require('node:fs');const os=require('node:os');const path=require('node:path');const crypto=require('node:crypto');const {execFileSync}=require('node:child_process');
const {McpServer}=require('@modelcontextprotocol/sdk/server/mcp.js');const {StdioServerTransport}=require('@modelcontextprotocol/sdk/server/stdio.js');const {z}=require('zod');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'dd-connect-'));fs.chmodSync(dir,0o700);
const key=path.join(dir,'key.pem'),cert=path.join(dir,'cert.pem');
execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-keyout',key,'-out',cert,'-days','1','-subj','/CN=droiddesk-local'],{stdio:'ignore'});fs.chmodSync(key,0o600);
const pem=fs.readFileSync(cert);const pin=new crypto.X509Certificate(pem).fingerprint256.replaceAll(':','').toLowerCase();const token=crypto.randomBytes(32).toString('hex');
let job=null;let lastPoll=0;
function send(res,status,body){res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(body));}
function authenticate(req){let got=Buffer.from(req.headers.authorization||'');let expected=Buffer.from('Bearer '+token);return got.length===expected.length&&crypto.timingSafeEqual(got,expected);}
const api=https.createServer({key:fs.readFileSync(key),cert:pem},(req,res)=>{
 if(!authenticate(req))return send(res,401,{error:'unauthorized'});
 if(req.method==='GET'&&req.url==='/poll'){lastPoll=Date.now();if(job&&!job.sent){job.sent=true;return send(res,200,{id:job.id,op:job.op,params:job.params});}return send(res,200,{});}
 if(req.method==='POST'&&req.url==='/result'){let data='';req.on('data',chunk=>{data+=chunk;if(data.length>1024*1024)req.destroy();});req.on('end',()=>{try{const v=JSON.parse(data);if(!job||!job.sent||v.id!==job.id)return send(res,409,{error:'unknown_job'});const pending=job;job=null;clearTimeout(pending.timer);if(v.error)pending.reject(Error(v.error));else pending.resolve(v.result);send(res,200,{ok:true});}catch(e){send(res,400,{error:'bad_request'});}});return;}
 send(res,404,{error:'not_found'});
});
api.on('error',e=>{console.error('HTTPS server error:',e.message);process.exit(1)});
function call(op,params={}) {return new Promise((resolve,reject)=>{
 if(job)return reject(Error('Another command is in progress'));if(Date.now()-lastPoll>10000)return reject(Error('Phone is not connected or session was stopped'));
 const id=crypto.randomUUID();job={id,op,params,resolve,reject,sent:false};job.timer=setTimeout(()=>{if(job?.id===id){const sent=job.sent;job=null;reject(Error(sent?'Result unknown: check the phone before retrying':'Phone did not receive the action'));}},15000);
});}
const server=new McpServer({name:'droiddesk-android-local',version:'0.1.0'});
function register(name,description,schema,op){server.registerTool(name,{description,inputSchema:schema},async params=>{try{let result=await call(op,params);return {content:[{type:'text',text:JSON.stringify(result)}]};}catch(e){return {isError:true,content:[{type:'text',text:e.message}]};}});}
register('get_screen','Read the current screen accessibility elements; text may contain private information.',{},'screen');
register('tap','Tap a point in screen pixels. Inspect screen first.',{x:z.number().int().nonnegative(),y:z.number().int().nonnegative()},'tap');
register('swipe','Swipe between two pixel coordinates.',{x:z.number().int().nonnegative(),y:z.number().int().nonnegative(),x2:z.number().int().nonnegative(),y2:z.number().int().nonnegative()},'swipe');
register('type_text','Replace text in the currently focused non-password field.',{text:z.string().max(2000)},'text');
register('press_back','Press Android Back.',{},'back');register('press_home','Press Android Home.',{},'home');
register('open_url','Open an HTTP(S) URL.',{url:z.string().url().max(2048)},'open_url');register('open_app','Open an installed app by package name.',{package:z.string().max(256)},'open_app');
server.registerTool('screenshot',{description:'Get a screenshot on Android 11 or newer.',inputSchema:{}},async()=>{try{const r=await call('screenshot');return {content:[{type:'image',data:r.jpeg,mimeType:'image/jpeg'}]};}catch(e){return {isError:true,content:[{type:'text',text:e.message}]};}});
server.registerTool('phone_status',{description:'Check whether the phone is polling the server.',inputSchema:{}},async()=>({content:[{type:'text',text:JSON.stringify({connected:Date.now()-lastPoll<10000})}]}));
const host=process.env.DD_BIND||'0.0.0.0';const port=Number(process.env.DD_PORT||8765);if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Invalid DD_PORT');
let sessionFileCreated=false;
api.listen(port,host,()=>{if(process.env.DD_SESSION_FILE){try{fs.writeFileSync(process.env.DD_SESSION_FILE,JSON.stringify({port,token,certificate_sha256:pin},null,2),{mode:0o600,flag:'wx'});sessionFileCreated=true;}catch(e){console.error('Cannot create session file:',e.message);process.exit(1);}}console.error(`Phone HTTPS port: ${port}; connect using this computer's LAN IP`);console.error(`Session token: ${token}`);console.error(`Certificate SHA-256: ${pin}`);console.error('Values change after restart; keep them private.');});
function cleanup(){api.close();fs.rmSync(dir,{recursive:true,force:true});if(sessionFileCreated)try{fs.unlinkSync(process.env.DD_SESSION_FILE)}catch{}}process.on('exit',cleanup);for(const sig of ['SIGINT','SIGTERM'])process.on(sig,()=>process.exit(0));
server.connect(new StdioServerTransport()).catch(e=>{console.error(e);process.exit(1)});
