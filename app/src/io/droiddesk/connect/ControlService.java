package io.droiddesk.connect;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.net.*;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;
import javax.net.ssl.*;

public final class ControlService extends AccessibilityService {
  static volatile ControlService instance;
  private volatile boolean active=false;
  private volatile Thread worker;
  private volatile HttpsURLConnection current;
  @Override protected void onServiceConnected(){instance=this;}
  @Override public void onAccessibilityEvent(AccessibilityEvent event){}
  @Override public void onInterrupt(){stopSession();}
  @Override public void onDestroy(){stopSession(); instance=null;super.onDestroy();}
  synchronized void startSession(String url,String token,String fingerprint) throws Exception {
    stopSession();
    final String pin=fingerprint.replace(":","").toLowerCase();
    if(pin.length()!=64)throw new IllegalArgumentException("Invalid certificate fingerprint");
    SSLContext context=SSLContext.getInstance("TLS");
    context.init(null,new TrustManager[]{new X509TrustManager(){
      public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
      public void checkClientTrusted(X509Certificate[] c,String a) throws java.security.cert.CertificateException {throw new java.security.cert.CertificateException("No client certificates");}
      public void checkServerTrusted(X509Certificate[] c,String a) throws java.security.cert.CertificateException {try{
        if(c==null||c.length==0)throw new Exception(); byte[] digest=MessageDigest.getInstance("SHA-256").digest(c[0].getEncoded());
        StringBuilder b=new StringBuilder();for(byte x:digest)b.append(String.format(java.util.Locale.US,"%02x",x & 255));
        if(!MessageDigest.isEqual(b.toString().getBytes("UTF-8"),pin.getBytes("UTF-8")))throw new Exception();
      }catch(Exception e){throw new java.security.cert.CertificateException("Certificate fingerprint mismatch");}}
    }},new SecureRandom());
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel("control","Active remote control",NotificationManager.IMPORTANCE_LOW));
    Intent open=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification n=new Notification.Builder(this,"control").setContentTitle("DroidDesk Connect is active").setContentText("Tap to stop remote control").setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(pi).setOngoing(true).build();
    startForeground(93,n); active=true;
    worker=new Thread(()->loop(context,url,token),"phone-session"); worker.start();
  }
  synchronized void stopSession(){active=false;HttpsURLConnection c=current;if(c!=null)c.disconnect();Thread t=worker;if(t!=null)t.interrupt();worker=null;stopForeground(true);}
  private JSONObject request(SSLContext ctx,String url,String token,String path,JSONObject body) throws Exception {
    HttpsURLConnection c=(HttpsURLConnection)new URL(url+path).openConnection();current=c;c.setSSLSocketFactory(ctx.getSocketFactory());c.setHostnameVerifier((hostname,session)->true);c.setInstanceFollowRedirects(false); // Identity is verified by the pinned certificate; never forward the token via redirects.
    c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setRequestProperty("Authorization","Bearer "+token);
    if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}}
    try(InputStream in=c.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(b.size()+n>65536)throw new IOException("Response too large");b.write(buf,0,n);}return new JSONObject(b.toString("UTF-8"));}finally{c.disconnect();current=null;}
  }
  private void loop(SSLContext ctx,String url,String token){
    try{while(active&&worker==Thread.currentThread()){try{JSONObject job=request(ctx,url,token,"/poll",null);if(job.has("id")&&active&&worker==Thread.currentThread()){JSONObject res=new JSONObject();res.put("id",job.getString("id"));try{res.put("result",execute(job));}catch(Exception e){res.put("error",e.getMessage()==null?"Action failed":e.getMessage());}if(active&&worker==Thread.currentThread())request(ctx,url,token,"/result",res);}else Thread.sleep(500);}catch(Exception e){if(active&&worker==Thread.currentThread())Thread.sleep(1500);}}}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
  }
  private JSONObject execute(JSONObject job) throws Exception {
    if(!active)throw new Exception("Session stopped");String op=job.getString("op");JSONObject p=job.optJSONObject("params");if(p==null)p=new JSONObject();
    JSONObject out=new JSONObject();
    if(op.equals("screen")){AccessibilityNodeInfo root=getRootInActiveWindow();JSONArray nodes=new JSONArray();if(root!=null)walk(root,nodes,0);out.put("elements",nodes);return out;}
    if(op.equals("screenshot")){if(android.os.Build.VERSION.SDK_INT<30)throw new Exception("Screenshot requires Android 11 or newer");CountDownLatch done=new CountDownLatch(1);String[] result={null};int[] error={0};takeScreenshot(android.view.Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){public void onSuccess(ScreenshotResult shot){try{android.graphics.Bitmap hardware=android.graphics.Bitmap.wrapHardwareBuffer(shot.getHardwareBuffer(),shot.getColorSpace());if(hardware==null){error[0]=-1;return;}android.graphics.Bitmap bitmap=hardware.copy(android.graphics.Bitmap.Config.ARGB_8888,false);int w=bitmap.getWidth(),h=bitmap.getHeight();float scale=Math.min(1f,1024f/Math.max(w,h));android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,70,bytes);result[0]=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);}finally{shot.getHardwareBuffer().close();done.countDown();}}public void onFailure(int code){error[0]=code;done.countDown();}});if(!done.await(5,TimeUnit.SECONDS)||result[0]==null)throw new Exception("Screenshot unavailable: "+error[0]);out.put("jpeg",result[0]);return out;}
    if(op.equals("open_url")){String u=p.getString("url");java.net.URI uri=new java.net.URI(u);if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme()))throw new Exception("Only HTTP(S) links allowed");Intent intent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(u));intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);out.put("success",true);return out;}
    if(op.equals("open_app")){String pkg=p.getString("package");Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null)throw new Exception("App not found or not visible");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);out.put("success",true);return out;}
    if(op.equals("tap")||op.equals("swipe")){int x=p.getInt("x"),y=p.getInt("y"),x2=p.optInt("x2",x),y2=p.optInt("y2",y);android.util.DisplayMetrics dm=getResources().getDisplayMetrics();if(x<0||y<0||x2<0||y2<0||x>=dm.widthPixels||x2>=dm.widthPixels||y>=dm.heightPixels||y2>=dm.heightPixels)throw new Exception("Coordinates outside screen");Path path=new Path();path.moveTo(x,y);if(op.equals("swipe"))path.lineTo(x2,y2);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,op.equals("tap")?80:400)).build();CountDownLatch done=new CountDownLatch(1);boolean[] ok={false};if(!dispatchGesture(gesture,new GestureResultCallback(){public void onCompleted(GestureDescription g){ok[0]=true;done.countDown();}public void onCancelled(GestureDescription g){done.countDown();}},null))throw new Exception("Gesture rejected");if(!done.await(4,TimeUnit.SECONDS))throw new Exception("Gesture result unknown");out.put("success",ok[0]);return out;}
    if(op.equals("text")){AccessibilityNodeInfo root=getRootInActiveWindow();AccessibilityNodeInfo focused=root==null?null:root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);if(focused==null||focused.isPassword())throw new Exception("No editable non-password field focused");Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,p.getString("text"));out.put("success",focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));return out;}
    if(op.equals("back")||op.equals("home")){out.put("success",performGlobalAction(op.equals("back")?GLOBAL_ACTION_BACK:GLOBAL_ACTION_HOME));return out;}
    throw new Exception("Unsupported action");
  }
  private void walk(AccessibilityNodeInfo node,JSONArray result,int depth)throws Exception{if(node==null||depth>24||result.length()>=400)return;Rect b=new Rect();node.getBoundsInScreen(b);JSONObject item=new JSONObject();item.put("class",String.valueOf(node.getClassName()));item.put("text",node.isPassword()?"[redacted]":String.valueOf(node.getText()==null?"":node.getText()));item.put("description",node.isPassword()?"[redacted]":String.valueOf(node.getContentDescription()==null?"":node.getContentDescription()));item.put("bounds",new JSONArray().put(b.left).put(b.top).put(b.right).put(b.bottom));item.put("clickable",node.isClickable());result.put(item);for(int i=0;i<node.getChildCount();i++)walk(node.getChild(i),result,depth+1);}
}
