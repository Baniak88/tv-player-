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
    static long timestamp(JsonObject object,String field,String fallback,String timezone) {
        try {
            long value=Long.parseLong(field(object,field,"0"));
            if(value>100000000000L) value/=1000;
            if(value>0) return value;
        } catch(NumberFormatException ignored) {}
        try {
            return LocalDateTime.parse(field(object,fallback,""),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(zone(timezone)).toEpochSecond();
        } catch(java.time.DateTimeException e) { return 0; }
    }
    static ZoneId zone(String timezone) {
        try { return ZoneId.of(timezone); } catch(java.time.DateTimeException e) { return ZoneOffset.UTC; }
    }
    public static List<Program> parse(Reader reader,String timezone) throws IOException {
        try {
            JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement value=root.get("epg_listings");
            List<Program> programs=new ArrayList<>();
            if(value==null || value.isJsonNull()) return programs;
            if(!value.isJsonArray()) throw new IOException("Nieprawidłowy program TV.");
            for(JsonElement item:value.getAsJsonArray()) {
                if(!item.isJsonObject()) continue;
                JsonObject entry=item.getAsJsonObject();
                long start=timestamp(entry,"start_timestamp","start",timezone);
                long end=timestamp(entry,entry.has("stop_timestamp")?"stop_timestamp":"end_timestamp","end",timezone);
                if(start<=0 || end<=start) continue;
                String serverStart=field(entry,"start","");
                if(!serverStart.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}"))
                    serverStart=Instant.ofEpochSecond(start).atZone(zone(timezone)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                programs.add(new Program(decode(field(entry,"title","Program")),decode(field(entry,"description","")),start,end,serverStart));
            }
            programs.sort(Comparator.comparingLong(Program::start)); return programs;
        } catch(JsonParseException | IllegalStateException e) { throw new IOException("Nieprawidłowa odpowiedź EPG.",e); }
    }
    public static HttpUrl endpoint(Channel channel) throws IOException {
        if(!channel.guideUrl().isEmpty()) return SourceClient.validate(channel.guideUrl());
        HttpUrl stream=SourceClient.validate(channel.url()); List<String> parts=stream.pathSegments();
        int live=parts.indexOf("live");
        if(live<0 || parts.size()!=live+4) throw new IOException("EPG jest dostępne dla kanałów Xtream. Zaimportuj konto Xtream.");
        String id=parts.get(live+3).split("\\.")[0];
        HttpUrl.Builder base=stream.newBuilder().query(null);
        for(int i=parts.size()-1;i>=live;i--) base.removePathSegment(i);
        return base.addPathSegment("player_api.php").addQueryParameter("username",parts.get(live+1))
            .addQueryParameter("password",parts.get(live+2)).addQueryParameter("action","get_simple_data_table").addQueryParameter("stream_id",id).build();
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
