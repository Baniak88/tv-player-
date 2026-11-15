package pl.tvplayer;

import android.app.*;
import android.content.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

final class TvTestUi {
    static Instrumentation instrument() { return InstrumentationRegistry.getInstrumentation(); }
    static Context context() { return instrument().getTargetContext(); }
    static void waitFor(String message,BooleanSupplier condition) throws Exception {
        long until=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<until) { if(condition.getAsBoolean()) return; Thread.sleep(250); }
        assertTrue(message,condition.getAsBoolean());
    }
    static View find(View root,String text) {
        if(root instanceof TextView && text.equals(((TextView)root).getText().toString())) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=find(((ViewGroup)root).getChildAt(i),text); if(found!=null) return found; }
        return null;
    }
    static AccessibilityNodeInfo node(String text) {
        AccessibilityNodeInfo root=instrument().getUiAutomation().getRootInActiveWindow(); if(root==null) return null;
        AccessibilityNodeInfo result=null;
        for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(text)) {
            if(result==null && found.getText()!=null && text.equalsIgnoreCase(found.getText().toString())) result=found; else found.recycle();
        }
        root.recycle(); return result;
    }
    static void click(String text) throws Exception {
        waitFor("Missing option: "+text,() -> node(text)!=null);
        AccessibilityNodeInfo item=node(text); while(!item.isClickable() && item.getParent()!=null) item=item.getParent();
        assertTrue("Cannot click "+text,item.performAction(AccessibilityNodeInfo.ACTION_CLICK)); item.recycle(); instrument().waitForIdleSync();
    }
    static void key(int code) throws Exception {
        try(android.os.ParcelFileDescriptor.AutoCloseInputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand("input keyevent "+code))) { byte[] buffer=new byte[256]; while(input.read(buffer)!=-1) {} }
        instrument().waitForIdleSync(); Thread.sleep(200);
    }
    /** TCG can show a system-server ANR after boot. Never dismiss an application ANR. */
    static void systemReady() throws Exception {
        AccessibilityNodeInfo system=node("Process system isn't responding");
        if(system==null) return;
        system.recycle(); android.util.Log.w("TvTestUi","Dismissing emulator system-server boot ANR with Wait");
        click("Wait");
    }
    static void input(int index,String value) {
        java.util.List<AccessibilityNodeInfo> inputs=new java.util.ArrayList<>(); collectInputs(instrument().getUiAutomation().getRootInActiveWindow(),inputs);
        assertTrue("Missing input "+index,inputs.size()>index); android.os.Bundle args=new android.os.Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
        assertTrue(inputs.get(index).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)); instrument().waitForIdleSync();
    }
    private static void collectInputs(AccessibilityNodeInfo node,java.util.List<AccessibilityNodeInfo> inputs) {
        if(node==null) return; if("android.widget.EditText".contentEquals(node.getClassName())) inputs.add(node);
        for(int i=0;i<node.getChildCount();i++) collectInputs(node.getChild(i),inputs);
    }
    static Object field(Object target,String name) throws Exception { java.lang.reflect.Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    static void invoke(Object target,String name) {
        instrument().runOnMainSync(() -> { try { java.lang.reflect.Method method=target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target); } catch(Exception e) { throw new AssertionError(e); } });
        instrument().waitForIdleSync();
    }
    static void close(Activity activity) { if(activity!=null && !activity.isDestroyed()) { instrument().runOnMainSync(activity::finish); instrument().waitForIdleSync(); } }
}
