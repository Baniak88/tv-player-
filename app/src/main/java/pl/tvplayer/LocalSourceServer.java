package pl.tvplayer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One short-lived, PIN-protected LAN form. Never serves stored account data. */
final class LocalSourceServer implements AutoCloseable {
    record Submission(String type,String server,String user,String password) {}
    private final ServerSocket listener;
    private final Consumer<Submission> received;
    private final ExecutorService clients=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2));
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean accepted=new AtomicBoolean();
    private final String code=String.format(Locale.ROOT,"%06d",new SecureRandom().nextInt(1000000));
    private final long expires=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
    private final String authority;
    private volatile boolean closed;
    private int failures;
    LocalSourceServer(InetAddress address,Consumer<Submission> received) throws IOException {
        this.received=received; listener=new ServerSocket(0,4,address); listener.setSoTimeout(1000);
        authority=address.getHostAddress()+":"+listener.getLocalPort();
        Thread thread=new Thread(this::accept,"source-pairing"); thread.setDaemon(true); thread.start();
    }
    String url() { return "http://"+authority+"/"; }
    String code() { return code; }
    private void accept() {
        try {
            while(!closed && System.nanoTime()<expires) {
                try {
                    Socket socket=listener.accept(); socket.setSoTimeout(3000); sockets.add(socket);
                    try { clients.execute(() -> handle(socket)); }
                    catch(RejectedExecutionException busy) { sockets.remove(socket); socket.close(); }
                } catch(SocketTimeoutException timeout) { /* Expiry also works without any client. */ }
            }
        } catch(IOException ignored) { /* Dialog closure stops accept. */ }
        finally { close(); }
    }
    private void handle(Socket socket) {
        try(socket) {
            InputStream input=socket.getInputStream(); String request=line(input); if(request==null) return;
            String[] parts=request.split(" ");
            if(parts.length!=3) { reply(socket,400,"Nieprawidłowe żądanie."); return; }
            Map<String,String> headers=new HashMap<>(); int size=request.length();
            for(String header;(header=line(input))!=null && !header.isEmpty();) {
                size+=header.length(); if(size>8192) { reply(socket,413,"Za duże żądanie."); return; }
                int colon=header.indexOf(':'); if(colon<1) { reply(socket,400,"Nieprawidłowe nagłówki."); return; }
                String key=header.substring(0,colon).trim().toLowerCase(Locale.ROOT);
                if(headers.put(key,header.substring(colon+1).trim())!=null) { reply(socket,400,"Powtórzone nagłówki."); return; }
            }
            if(!authority.equalsIgnoreCase(headers.get("host"))) { reply(socket,403,"Otwórz adres wyświetlony na TV."); return; }
            if(parts[0].equals("GET") && parts[1].equals("/")) { reply(socket,200,FORM); return; }
            if(!parts[0].equals("POST") || !parts[1].equals("/source")) { reply(socket,404,"Nie znaleziono strony."); return; }
            String origin=headers.get("origin");
            if(origin!=null && !origin.equals("http://"+authority)) { reply(socket,403,"Otwórz formularz bezpośrednio z adresu TV."); return; }
            if(headers.containsKey("transfer-encoding") || !headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) { reply(socket,400,"Nieprawidłowy formularz."); return; }
            int length; try { length=Integer.parseInt(headers.getOrDefault("content-length","-1")); } catch(NumberFormatException e) { length=-1; }
            if(length<0 || length>16384) { reply(socket,413,"Za duże żądanie."); return; }
            byte[] body=new byte[length]; int at=0;
            while(at<length) { int n=input.read(body,at,length-at); if(n<0) return; at+=n; }
            Map<String,String> form=new HashMap<>();
            for(String item:new String(body,StandardCharsets.UTF_8).split("&")) {
                String[] field=item.split("=",2);
                String key=URLDecoder.decode(field[0],"UTF-8"),value=URLDecoder.decode(field.length>1?field[1]:"","UTF-8");
                if(form.put(key,value)!=null) { reply(socket,400,"Powtórzone pola."); return; }
            }
            boolean authorized;
            synchronized(this) {
                authorized=!closed && failures<5 && System.nanoTime()<expires && code.equals(form.get("code"));
                if(!authorized) failures++;
            }
            if(!authorized) { reply(socket,403,"Nieprawidłowy lub wygasły kod. Sprawdź kod na TV."); return; }
            String type=form.getOrDefault("type",""),server=form.getOrDefault("server","").trim(),user=form.getOrDefault("user","").trim(),password=form.getOrDefault("password","");
            try { SourceClient.validate(server); } catch(IllegalArgumentException invalid) { reply(socket,400,"Podaj adres zaczynający się od http:// lub https://. Wróć do formularza i popraw dane."); return; }
            if(!(type.equals("m3u") || type.equals("xtream")) || (type.equals("xtream") && (user.isEmpty() || password.isEmpty()))) { reply(socket,400,"Wpisz typ źródła, login i hasło. Wróć do formularza i popraw dane."); return; }
            if(!accepted.compareAndSet(false,true)) { reply(socket,409,"Dane zostały już przekazane do TV."); return; }
            Submission submission=new Submission(type,server,type.equals("xtream")?user:"",type.equals("xtream")?password:"");
            try { reply(socket,200,"Dane przekazane do TV. Sprawdź postęp pobierania na telewizorze. Możesz zamknąć tę stronę."); }
            finally { try { received.accept(submission); } finally { close(); } }
        } catch(IOException | IllegalArgumentException ignored) { /* No credentials or request bodies in logs. */ }
        finally { sockets.remove(socket); }
    }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int b;(b=input.read())!=-1;) {
            if(b=='\n') return bytes.toString("US-ASCII").replace("\r","");
            if(bytes.size()>=8192) throw new IOException("Header too long"); bytes.write(b);
        }
        return null;
    }
    private static void reply(Socket socket,int status,String text) throws IOException {
        String page=text.startsWith("<!doctype")?text:"<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>TV Player</title><p>"+text+"</p>";
        byte[] body=page.getBytes(StandardCharsets.UTF_8);
        String header="HTTP/1.1 "+status+" Response\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+body.length+"\r\nCache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: same-origin\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'\r\n\r\n";
        OutputStream output=socket.getOutputStream(); output.write(header.getBytes(StandardCharsets.US_ASCII)); output.write(body); output.flush();
    }
    @Override public void close() {
        closed=true; try { listener.close(); } catch(IOException ignored) {}
        for(Socket socket:sockets) try { socket.close(); } catch(IOException ignored) {}
        clients.shutdownNow();
    }
    private static final String FORM="""
        <!doctype html><html lang="pl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Dodaj źródło do TV Player</title><style>
        body{font:17px system-ui;background:#10151f;color:#f5f7fc;margin:0;padding:24px}main{max-width:440px;margin:auto}
        label{display:block;margin:18px 0 6px}input,select,button{font:inherit;box-sizing:border-box;width:100%;padding:14px;border-radius:10px;border:1px solid #667080;background:#1b2432;color:white}
        button{background:#bded73;color:#10151f;font-weight:700;margin-top:24px}p{color:#b9c1ce;line-height:1.5}h1{font-size:27px}</style>
        <main><h1>Dodaj źródło do TV</h1><p>Wpisz kod z telewizora i dane od dostawcy. Telefon i TV muszą być w tej samej sieci.</p>
        <form method="post" action="/source" autocomplete="off">
        <label for="code">Jednorazowy kod z TV</label><input id="code" name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required>
        <label for="type">Rodzaj źródła</label><select id="type" name="type"><option value="xtream">Konto Xtream (serwer, login, hasło)</option><option value="m3u">Link do listy M3U</option></select>
        <label for="server">Adres serwera lub link M3U</label><input id="server" name="server" type="url" placeholder="http://adres-dostawcy:port" autocapitalize="none" spellcheck="false" required maxlength="8192">
        <div id="account"><label for="user">Login</label><input id="user" name="user" autocapitalize="none" spellcheck="false" required maxlength="512">
        <label for="password">Hasło</label><input id="password" name="password" type="password" required maxlength="512"></div>
        <button>Połącz telewizor</button></form><p>Dane trafiają bezpośrednio do TV przez lokalne HTTP. Korzystaj z zaufanej sieci domowej.</p></main>
        <script>document.getElementById('type').addEventListener('change',function(){var x=this.value==='xtream';document.getElementById('account').hidden=!x;['user','password'].forEach(function(id){document.getElementById(id).required=x;});});</script></html>
        """;
}
