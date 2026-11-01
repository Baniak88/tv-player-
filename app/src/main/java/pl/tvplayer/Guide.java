package pl.tvplayer;

import com.google.gson.*;
import okhttp3.HttpUrl;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Guide {
    public record Program(String title,String description,long start,long end,String serverStart) {
        public boolean current(long now) { return start<=now && end>now; }
        public boolean replayable(Channel channel,long now) {
            return channel.archiveDays()>0 && start<now && end>start && now-start<channel.archiveDays()*86400L;
        }
    }
    static String field(JsonObject object,String name,String fallback) {
        JsonElement value=object.get(name); return value!=null && value.isJsonPrimitive()?value.getAsString():fallback;
    }
    static String decode(String text) {
        try {
            byte[] bytes=Base64.getDecoder().decode(text);
            String decoded=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            for(char c:decoded.toCharArray()) if(Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t') return text;
            return decoded;
        } catch(IllegalArgumentException | CharacterCodingException e) { return text; }
    }
    static ZoneId zone(String timezone) {
        try { return ZoneId.of(timezone); } catch(java.time.DateTimeException | NullPointerException e) { return ZoneOffset.UTC; }
    }
    static long instant(String value,String timezone) {
        if(value==null || value.isEmpty()) return 0;
        try { long stamp=Long.parseLong(value); return stamp>100000000000L?stamp/1000:stamp; } catch(NumberFormatException ignored) {}
        try { return Instant.parse(value).getEpochSecond(); } catch(java.time.DateTimeException ignored) {}
        try { return OffsetDateTime.parse(value).toEpochSecond(); } catch(java.time.DateTimeException ignored) {}
        for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm"}) {
            try { return LocalDateTime.parse(value,DateTimeFormatter.ofPattern(pattern)).atZone(zone(timezone)).toEpochSecond(); }
            catch(java.time.DateTimeException ignored) {}
        }
        return 0;
    }
    private static long time(JsonObject object,String timezone,String... fields) {
        for(String key:fields) { long value=instant(field(object,key,""),timezone); if(value>0) return value; }
        return 0;
    }
    public static List<Program> parse(Reader reader,String timezone) throws IOException {
        try {
            JsonElement root=JsonParser.parseReader(reader);
            JsonElement value=root;
            if(root.isJsonObject()) {
                value=root.getAsJsonObject().get("epg_listings");
                if(value==null) value=root.getAsJsonObject().get("epg_list");
            }
            List<Program> programs=new ArrayList<>();
            if(value==null || value.isJsonNull()) return programs;
            if(!value.isJsonArray()) throw new IOException("Nieprawidłowy program TV.");
            for(JsonElement item:value.getAsJsonArray()) {
                if(!item.isJsonObject()) continue; JsonObject entry=item.getAsJsonObject();
                long start=time(entry,timezone,"start_timestamp","start","start_time");
                long end=time(entry,timezone,"stop_timestamp","end_timestamp","end","stop","end_time");
                if(start<=0) continue;
                String raw=field(entry,"start","");
                if(!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"))
                    raw=Instant.ofEpochSecond(start).atZone(zone(timezone)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                programs.add(new Program(decode(field(entry,"title","Program")),decode(field(entry,"description","")),start,end,raw));
            }
            programs.sort(Comparator.comparingLong(Program::start));
            List<Program> valid=new ArrayList<>();
            for(int i=0;i<programs.size();i++) {
                Program p=programs.get(i); long end=p.end();
                if(end<=p.start() && i+1<programs.size()) end=programs.get(i+1).start();
                if(end>p.start()) valid.add(new Program(p.title(),p.description(),p.start(),end,p.serverStart()));
            }
            return valid;
        } catch(JsonParseException | IllegalStateException e) { throw new IOException("Nieprawidłowa odpowiedź EPG.",e); }
    }
    public static HttpUrl endpoint(Channel channel) throws IOException {
        HttpUrl api=SourceClient.accountEndpoint(channel);
        if(api==null) throw new IOException("Brak adresu API EPG.");
        String id=api.queryParameter("stream_id");
        if(id==null) {
            List<String> parts=SourceClient.validate(channel.url()).pathSegments();
            id=parts.get(parts.size()-1).split("\\.")[0];
        }
        if(!id.matches("[0-9]+")) throw new IOException("Brak identyfikatora kanału w API EPG.");
        return api.newBuilder().setQueryParameter("action","get_simple_data_table").setQueryParameter("stream_id",id).build();
    }
    static HttpUrl xmltv(Channel channel) throws IOException {
        if(channel.guideUrl().startsWith("xmltv:")) return SourceClient.validate(channel.guideUrl().substring(6));
        HttpUrl api=SourceClient.accountEndpoint(channel); if(api==null) return null;
        return api.newBuilder().query(null).removePathSegment(api.pathSize()-1).addPathSegment("xmltv.php")
            .addQueryParameter("username",api.queryParameter("username")).addQueryParameter("password",api.queryParameter("password")).build();
    }
    public static String replay(Channel channel,Program program,long now) throws IOException {
        if(!program.replayable(channel,now)) throw new IOException("Audycja nie mieści się w archiwum dostawcy.");
        HttpUrl api=endpoint(channel); List<String> parts=api.pathSegments();
        HttpUrl.Builder base=api.newBuilder().query(null).removePathSegment(parts.size()-1);
        String stamp=program.serverStart().substring(0,16).replace(' ',':').replaceFirst("([0-9]{2}):([0-9]{2})$","$1-$2");
        long duration=Math.max(1,(Math.min(program.end(),now)-program.start()+59)/60);
        return base.addPathSegment("timeshift").addPathSegment(api.queryParameter("username"))
            .addPathSegment(api.queryParameter("password")).addPathSegment(String.valueOf(duration))
            .addPathSegment(stamp).addPathSegment(api.queryParameter("stream_id")+".m3u8").build().toString();
    }
}
