package pl.tvplayer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalSourceServerTest {
    private final OkHttpClient client=new OkHttpClient();
    private LocalSourceServer server(AtomicReference<LocalSourceServer.Submission> received) throws IOException {
        return new LocalSourceServer(InetAddress.getLoopbackAddress(),received::set);
    }
    private Response post(LocalSourceServer server,String code,String type) throws IOException {
        return client.newCall(new Request.Builder().url(server.url()+"source").post(new FormBody.Builder()
            .add("code",code).add("type",type).add("server","https://provider.example:8080/abc?x=1&y=2")
            .add("user","user+żółć").add("password","p+& =?#").build()).build()).execute();
    }
    @Test public void phoneFormContainsNoAccountOrPinAndIsNotCached() throws Exception {
        try(LocalSourceServer server=server(new AtomicReference<>())) {
            try(Response response=client.newCall(new Request.Builder().url(server.url()).build()).execute()) {
                assertEquals(200,response.code()); assertEquals("no-store",response.header("Cache-Control"));
                String html=response.body().string(); assertTrue(html.contains("name=\"code\"")); assertTrue(html.contains("value=\"m3u\"")); assertFalse(html.contains(server.code()));
            }
        }
    }
    @Test public void wrongPinNeverImports() throws Exception {
        AtomicReference<LocalSourceServer.Submission> received=new AtomicReference<>();
        try(LocalSourceServer server=server(received)) {
            try(Response response=post(server,"wrong","xtream")) { assertEquals(403,response.code()); }
            assertNull(received.get());
        }
    }
    @Test public void validPinTransfersCredentialsExactlyOnceAndClosesPort() throws Exception {
        AtomicReference<LocalSourceServer.Submission> received=new AtomicReference<>();
        try(LocalSourceServer server=server(received)) {
            try(Response response=post(server,server.code(),"xtream")) { assertEquals(200,response.code()); response.body().string(); }
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while(received.get()==null && System.nanoTime()<deadline) Thread.sleep(10);
            assertNotNull(received.get()); assertEquals("user+żółć",received.get().user()); assertEquals("p+& =?#",received.get().password());
            assertEquals("https://provider.example:8080/abc?x=1&y=2",received.get().server());
            server.close();
            try { client.newCall(new Request.Builder().url(server.url()).build()).execute().close(); fail("Pairing server still open"); }
            catch(IOException expected) { /* Closed after one submission. */ }
        }
    }
    @Test public void m3uNeedsNoAccount() throws Exception {
        AtomicReference<LocalSourceServer.Submission> received=new AtomicReference<>();
        try(LocalSourceServer server=server(received)) {
            Request request=new Request.Builder().url(server.url()+"source").post(new FormBody.Builder().add("code",server.code()).add("type","m3u").add("server","https://example.com/list.m3u?username=u&password=p").build()).build();
            try(Response response=client.newCall(request).execute()) { assertEquals(200,response.code()); response.body().string(); }
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while(received.get()==null && System.nanoTime()<deadline) Thread.sleep(10);
            assertEquals("m3u",received.get().type()); assertEquals("",received.get().user()); assertEquals("",received.get().password());
        }
    }
    @Test public void crossOriginPostCannotChangeSource() throws Exception {
        AtomicReference<LocalSourceServer.Submission> received=new AtomicReference<>();
        try(LocalSourceServer server=server(received)) {
            Request request=new Request.Builder().url(server.url()+"source").header("Origin","https://other.example")
                .post(new FormBody.Builder().add("code",server.code()).add("type","m3u").add("server","https://example.com/list.m3u").build()).build();
            try(Response response=client.newCall(request).execute()) { assertEquals(403,response.code()); }
            assertNull(received.get());
        }
    }
    @Test public void fiveWrongPinsLockTheSession() throws Exception {
        AtomicReference<LocalSourceServer.Submission> received=new AtomicReference<>();
        try(LocalSourceServer server=server(received)) {
            for(int i=0;i<5;i++) try(Response response=post(server,"wrong","xtream")) { assertEquals(403,response.code()); }
            try(Response response=post(server,server.code(),"xtream")) { assertEquals(403,response.code()); }
            assertNull(received.get());
        }
    }
    @Test public void excessiveRequestBodyIsRejectedBeforeReading() throws Exception {
        try(LocalSourceServer server=server(new AtomicReference<>())) {
            URI url=URI.create(server.url());
            try(Socket socket=new Socket(url.getHost(),url.getPort())) {
                socket.setSoTimeout(2000);
                socket.getOutputStream().write(("POST /source HTTP/1.1\r\nHost: "+url.getAuthority()+"\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 1000000\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                String status=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII)).readLine();
                assertTrue(status,status.contains("413"));
            }
        }
    }
}
