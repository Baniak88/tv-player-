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
    static void resetNavigation() { context().getSharedPreferences("tv-navigation",0).edit().clear().commit(); }
    static void landscape(int width,int height) throws Exception {
        String physical; try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand("wm size"));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) { byte[] bytes=new byte[512]; for(int n;(n=in.read(bytes))!=-1;) out.write(bytes,0,n); physical=out.toString("UTF-8"); }
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)").matcher(physical); assertTrue("Physical screen size missing",match.find()); boolean portrait=Integer.parseInt(match.group(1))<Integer.parseInt(match.group(2));
        for(String command:new String[]{"wm size "+(portrait?height+"x"+width:width+"x"+height),"wm density 160"}) try(android.os.ParcelFileDescriptor.AutoCloseInputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand(command))) { byte[] bytes=new byte[256]; while(in.read(bytes)!=-1) {} }
    }
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
        android.app.UiAutomation automation=instrument().getUiAutomation();
        android.accessibilityservice.AccessibilityServiceInfo service=automation.getServiceInfo();
        if(service!=null && (service.flags & android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS)==0) { service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS; automation.setServiceInfo(service); }
        AccessibilityNodeInfo root=automation.getRootInActiveWindow();
        // A dismissed dialog can remain API 25's "active" window; hardware input uses the focused one.
        if(root==null) for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows()) { if(root==null && window.isFocused()) root=window.getRoot(); window.recycle(); }
        if(root==null) return null;
        AccessibilityNodeInfo result=null;
        for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(text)) {
            if(result==null && found.getText()!=null && text.equalsIgnoreCase(found.getText().toString())) result=found; else found.recycle();
        }
        // API 25's text query can return an outdated result after replacing a catalogue subtree.
        if(result==null && root.refresh()) result=exactNode(root,text);
        root.recycle(); return result;
    }
    private static AccessibilityNodeInfo exactNode(AccessibilityNodeInfo root,String text) {
        if(!root.refresh()) return null;
        if(root.getText()!=null && text.equalsIgnoreCase(root.getText().toString())) return AccessibilityNodeInfo.obtain(root);
        for(int i=0;i<root.getChildCount();i++) { AccessibilityNodeInfo child=root.getChild(i); if(child==null) continue; AccessibilityNodeInfo found=exactNode(child,text); child.recycle(); if(found!=null) return found; }
        return null;
    }
    static void click(String text) throws Exception {
        try { waitFor("Missing option: "+text,() -> node(text)!=null); }
        catch(AssertionError failure) {
            AccessibilityNodeInfo active=instrument().getUiAutomation().getRootInActiveWindow();
            android.util.Log.e("TvTestUi","Missing "+text+"; active root="+active); if(active!=null) active.recycle();
            instrument().runOnMainSync(() -> { for(Activity activity:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) android.util.Log.e("TvTestUi","Resumed="+activity+" focus="+activity.getCurrentFocus()); });
            throw failure;
        }
        AccessibilityNodeInfo item=node(text); while(!item.isClickable() && item.getParent()!=null) item=item.getParent();
        assertTrue("Cannot click "+text,item.performAction(AccessibilityNodeInfo.ACTION_CLICK)); item.recycle(); instrument().waitForIdleSync();
    }
    static void key(int code) throws Exception {
        try(android.os.ParcelFileDescriptor.AutoCloseInputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument().getUiAutomation().executeShellCommand("input keyevent "+code))) { byte[] buffer=new byte[256]; while(input.read(buffer)!=-1) {} }
        instrument().waitForIdleSync(); Thread.sleep(200);
    }
    /** TCG can show a system-server ANR after boot. Never dismiss an application ANR. */
    static void systemReady() throws Exception {
        resetNavigation();
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
