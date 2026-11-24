'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');const https=require('node:https');
const {Client}=require('@modelcontextprotocol/sdk/client/index.js');const {StdioClientTransport}=require('@modelcontextprotocol/sdk/client/stdio.js');
function req(port,token,method,path,body){return new Promise((resolve,reject)=>{const r=https.request({hostname:'127.0.0.1',port,path,method,rejectUnauthorized:false,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'}},res=>{let s='';res.on('data',x=>s+=x);res.on('end',()=>resolve({status:res.statusCode,body:JSON.parse(s)}));});r.on('error',reject);r.end(body?JSON.stringify(body):undefined);});}
test('MCP local transport authenticates phone and relays actions',async()=>{
 const port=19000+Math.floor(Math.random()*10000);const transport=new StdioClientTransport({command:process.execPath,args:[require('node:path').join(__dirname,'index.cjs')],env:{...process.env,DD_BIND:'127.0.0.1',DD_PORT:String(port)},stderr:'pipe'});
 let log='';transport.stderr.on('data',x=>{log+=x});const client=new Client({name:'prototype-test',version:'1'});await client.connect(transport);
 try{for(let i=0;i<100&&!log.includes('Certificate SHA-256:');i++)await new Promise(r=>setTimeout(r,50));let m=log.match(/Session token: ([a-f0-9]{64})/);assert.ok(m,'server started');const token=m[1];
 assert.equal((await req(port,'wrong','GET','/poll')).status,401);
 assert.deepEqual((await req(port,token,'GET','/poll')).body,{});
 const listed=await client.listTools();assert.ok(listed.tools.some(t=>t.name==='get_screen'));
 const pending=client.callTool({name:'get_screen',arguments:{}});
 let command;for(let i=0;i<50;i++){let p=await req(port,token,'GET','/poll');if(p.body.id){command=p.body;break;}await new Promise(r=>setTimeout(r,20));}
 assert.equal(command.op,'screen');assert.equal((await req(port,token,'POST','/result',{id:command.id,result:{elements:[{text:'Test'}]}})).status,200);
 const result=await pending;assert.equal(JSON.parse(result.content[0].text).elements[0].text,'Test');
 assert.equal((await req(port,token,'POST','/result',{id:command.id,result:{}})).status,409);
 }finally{await client.close();}
});
