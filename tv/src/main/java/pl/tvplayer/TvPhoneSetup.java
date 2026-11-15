package pl.tvplayer;

import android.app.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.*;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import java.net.*;
import java.util.function.Consumer;

/** The listening socket lives only as long as the pairing dialog. */
final class TvPhoneSetup {
    private final Activity activity;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LocalSourceServer server;
    private AlertDialog dialog;
    TvPhoneSetup(Activity activity) { this.activity=activity; }
    void show(Consumer<TvAccountStore.Account> received) {
        close();
        try {
            InetAddress address=address();
            if(address==null) throw new java.io.IOException("Brak połączenia z siecią domową. Połącz TV z Wi-Fi lub kablem i spróbuj ponownie.");
            server=new LocalSourceServer(address,source -> ui.post(() -> {
                if(dialog==null || !dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
                close(); received.accept(new TvAccountStore.Account(source.type(),source.server(),source.user(),source.password()));
            }));
            LinearLayout content=new LinearLayout(activity); content.setGravity(Gravity.CENTER_VERTICAL); content.setPadding(dp(24),dp(12),dp(24),dp(12));
            ImageView qr=new ImageView(activity); qr.setContentDescription("Kod QR z lokalnym adresem formularza");
            int pixels=256; BitMatrix matrix=new MultiFormatWriter().encode(server.url(),BarcodeFormat.QR_CODE,pixels,pixels);
            int[] colors=new int[pixels*pixels]; for(int y=0;y<pixels;y++) for(int x=0;x<pixels;x++) colors[y*pixels+x]=matrix.get(x,y)?0xff000000:0xffffffff;
            Bitmap bitmap=Bitmap.createBitmap(colors,pixels,pixels,Bitmap.Config.RGB_565); qr.setImageBitmap(bitmap);
            content.addView(qr,new LinearLayout.LayoutParams(dp(190),dp(190)));
            TextView instructions=new TextView(activity); instructions.setTextSize(18); instructions.setPadding(dp(24),0,0,0);
            instructions.setText("1. Telefon i TV połącz z tą samą siecią.\n2. Zeskanuj QR lub otwórz w telefonie:\n"+server.url()+"\n\n3. Wpisz kod: "+server.code()+"\n\nWpisz dane od dostawcy na telefonie.\nFormularz zamknie się po 5 minutach.");
            content.addView(instructions,new LinearLayout.LayoutParams(0,-2,1));
            dialog=new AlertDialog.Builder(activity).setTitle("Dodaj źródło przez telefon").setView(content).setNegativeButton("Zamknij",(d,w) -> close()).create();
            dialog.setOnDismissListener(d -> close()); dialog.show();
            ui.postDelayed(() -> { close(); if(!activity.isFinishing() && !activity.isDestroyed()) Toast.makeText(activity,"Kod wygasł. Otwórz „Dodaj przez telefon” ponownie.",Toast.LENGTH_LONG).show(); },300000);
        } catch(Exception failure) {
            close(); new AlertDialog.Builder(activity).setTitle("Połączenie przez telefon").setMessage("Połącz telewizor i telefon z tą samą siecią domową. Sieć gościnna lub izolacja urządzeń w routerze może blokować połączenie. Spróbuj ponownie albo wpisz źródło pilotem.").setPositiveButton("OK",null).show();
        }
    }
    private InetAddress address() throws Exception {
        ConnectivityManager connectivity=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active=connectivity.getActiveNetwork();
        LinkProperties link=active==null?null:connectivity.getLinkProperties(active);
        if(link!=null) for(LinkAddress item:link.getLinkAddresses()) if(local(item.getAddress())) return item.getAddress();
        java.util.Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        if(interfaces!=null) while(interfaces.hasMoreElements()) {
            NetworkInterface network=interfaces.nextElement(); String name=network.getName();
            if(!network.isUp() || !(name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en"))) continue;
            java.util.Enumeration<InetAddress> addresses=network.getInetAddresses();
            while(addresses.hasMoreElements()) { InetAddress address=addresses.nextElement(); if(local(address)) return address; }
        }
        return null;
    }
    private boolean local(InetAddress address) { return address instanceof Inet4Address && address.isSiteLocalAddress() && !address.isLoopbackAddress(); }
    private int dp(int value) { return Ui.dp(activity,value); }
    void close() {
        ui.removeCallbacksAndMessages(null);
        if(server!=null) { server.close(); server=null; }
        AlertDialog previous=dialog; dialog=null; if(previous!=null && previous.isShowing()) previous.dismiss();
    }
}
