package io.droiddesk.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public final class MainActivity extends Activity {
  private EditText url, token, fingerprint;
  private TextView status;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout layout = new LinearLayout(this); layout.setPadding(32,32,32,32); layout.setOrientation(LinearLayout.VERTICAL);
    TextView title = new TextView(this); title.setText("DroidDesk Connect · prototype"); title.setTextSize(22); layout.addView(title);
    TextView intro = new TextView(this); intro.setText("Connect your own phone to your local MCP server. While active, the server can read and control your screen. Stop access at any time."); layout.addView(intro);
    url=field(layout,"Server URL, e.g. https://192.168.1.2:8765"); token=field(layout,"Session token"); token.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); fingerprint=field(layout,"Server certificate SHA-256 fingerprint");
    Button settings=button(layout,"1. Enable accessibility service"); settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    Button start=button(layout,"2. Start session"); start.setOnClickListener(v->{
      if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);show("Allow notifications, then start the session again.");return;}
      ControlService s=ControlService.instance;
      if(s==null){show("Enable the accessibility service first.");return;}
      String u=url.getText().toString().trim(), t=token.getText().toString().trim(), f=fingerprint.getText().toString().trim();
      if(!u.matches("https://[^/]+(:[0-9]+)?")||!t.matches("[a-f0-9]{64}")||!f.matches("[a-fA-F0-9:]{64,95}")){show("Enter an HTTPS URL, the 64-character token, and certificate fingerprint.");return;}
      try { s.startSession(u,t,f); show("Session started. Return here to stop access."); } catch(Exception e){show("Could not start: "+e.getMessage());}
    });
    Button stop=button(layout,"Stop session"); stop.setOnClickListener(v->{if(ControlService.instance!=null)ControlService.instance.stopSession();show("Session stopped.");});
    status=new TextView(this); layout.addView(status); setContentView(layout);
  }
  EditText field(LinearLayout l,String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);l.addView(e);return e;}
  Button button(LinearLayout l,String text){Button b=new Button(this);b.setText(text);l.addView(b);return b;}
  void show(String s){status.setText(s);}
}
