package com.pcopi.bydphonespace;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** An opt-in, privacy-minimal IME for transferring ONE saved route destination.
 * It reads no surrounding text, stores no keystrokes and sends no network data.
 * It is deliberately disabled for all apps other than GPack / virtual Maps.
 * The user must enable/select the keyboard through Android settings.
 */
public final class GpackNavKeyboardService extends InputMethodService {
    private static final int BG=0xff0b1828,FG=0xfff2f7fc,CYAN=0xff4dc4ff;
    private TextView state;
    private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
    private GradientDrawable drawable(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(10));return d;}
    private Button button(String s,boolean accent){
        Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(accent?BG:FG);
        b.setBackground(drawable(accent?CYAN:0xff223954));
        return b;
    }
    private boolean targetOkay(){
        EditorInfo info=getCurrentInputEditorInfo();
        if(info==null||info.packageName==null)return false;
        String pkg=info.packageName;
        if(!"com.gpack.agent".equals(pkg) && !"com.google.android.apps.maps".equals(pkg))return false;
        int clazz=info.inputType&InputType.TYPE_MASK_CLASS;
        if(clazz!=InputType.TYPE_CLASS_TEXT)return false;
        int variation=info.inputType&InputType.TYPE_MASK_VARIATION;
        return variation!=InputType.TYPE_TEXT_VARIATION_PASSWORD
            && variation!=InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            && variation!=InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }
    private void note(String text){if(state!=null)state.setText(text);}
    private void insert(){
        String query=GpackInputBridge.current(this);
        if(!targetOkay()){note("ใช้ได้เฉพาะช่องค้นหาใน GPack / Maps ไม่ทำงานกับแอปอื่น");return;}
        if(query.isEmpty()){note("ยังไม่มีจุดหมายจาก BYD Phone Space");return;}
        InputConnection input=getCurrentInputConnection();
        if(input==null){note("ยังไม่พบช่องรับข้อความ");return;}
        // Selection is sent as a command; existing typed contents are not inspected.
        input.beginBatchEdit();
        input.performContextMenuAction(android.R.id.selectAll);
        boolean inserted=input.commitText(query,1);
        input.finishComposingText();
        input.endBatchEdit();
        note(inserted?"ใส่จุดหมายแล้ว • กดค้นหาเพื่อตรวจว่า Maps พบสถานที่ตรง":"ไม่สามารถใส่ข้อความในช่องนี้ได้");
    }
    private void search(){
        if(!targetOkay()){note("กรุณาแตะช่องค้นหา Maps ก่อน");return;}
        InputConnection input=getCurrentInputConnection();
        if(input==null)return;
        EditorInfo info=getCurrentInputEditorInfo();
        int action=info.imeOptions&EditorInfo.IME_MASK_ACTION;
        if(action==EditorInfo.IME_ACTION_NONE||action==EditorInfo.IME_ACTION_UNSPECIFIED){
            action=EditorInfo.IME_ACTION_SEARCH;
        }
        boolean ok=input.performEditorAction(action);
        note(ok?"ส่งคำสั่งค้นหาแล้ว • ตรวจจุดหมายบน Maps จริง":"Maps ไม่รับคำสั่งค้นหา • กดค้นหาบนจอเอง");
    }
    @Override public View onCreateInputView(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(8),dp(10),dp(10));root.setBackgroundColor(BG);
        state=new TextView(this);state.setTextSize(14);state.setTextColor(FG);
        state.setText("BYD Nav Keyboard • รอจุดหมายจาก BYD Phone Space");
        state.setMaxLines(2);state.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(state,new LinearLayout.LayoutParams(-1,dp(48)));
        Button insert=button("ใส่จุดหมายจาก BYD Phone Space",true);
        insert.setOnClickListener(v->insert());
        root.addView(insert,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        Button search=button("ค้นหาใน Maps",false);
        search.setOnClickListener(v->search());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);
        p.setMargins(0,dp(8),dp(5),0);row.addView(search,p);
        Button switchIme=button("แป้นพิมพ์เดิม",false);
        switchIme.setOnClickListener(v->{
            InputMethodManager mgr=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(mgr!=null)mgr.showInputMethodPicker();
        });
        p=new LinearLayout.LayoutParams(0,dp(46),1);
        p.setMargins(dp(5),dp(8),0,0);row.addView(switchIme,p);
        root.addView(row);
        return root;
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        String query=GpackInputBridge.current(this);
        if(!targetOkay())note("รอช่องค้นหา Maps ใน GPack • ไม่ใส่ข้อความในแอปอื่น");
        else if(query.isEmpty())note("ยังไม่มีจุดหมาย กรุณาเลือกจุดหมายใน Phone Space ก่อน");
        else note("จุดหมายพร้อม: "+(query.length()>55?query.substring(0,55)+"…":query));
    }
}
