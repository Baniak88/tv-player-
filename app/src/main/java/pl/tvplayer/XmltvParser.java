package pl.tvplayer;
import org.xmlpull.v1.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
public final class XmltvParser {
    interface Sink {
        void channel(String id,String name) throws IOException;
        void program(String id,String title,String description,long start,long end) throws IOException;
    }
    static long time(String raw,String fallback) {
        if(raw==null) return 0; String value=raw.trim();
        try {
            String digits=value.replaceFirst("^([0-9]{12,14}).*$","$1");
            if(digits.length()!=12 && digits.length()!=14) return 0;
            String offset=value.substring(digits.length()).trim();
            ZoneId zone=offset.isEmpty()?Guide.zone(fallback):offset.equals("UTC")||offset.equals("GMT")?ZoneOffset.UTC:ZoneOffset.of(offset);
            return LocalDateTime.parse(digits,DateTimeFormatter.ofPattern(digits.length()==14?"yyyyMMddHHmmss":"yyyyMMddHHmm",Locale.ROOT)).atZone(zone).toEpochSecond();
        } catch(java.time.DateTimeException | IndexOutOfBoundsException e) { return 0; }
    }
    private static String text(XmlPullParser parser) throws IOException,XmlPullParserException {
        int depth=parser.getDepth(); StringBuilder result=new StringBuilder();
        for(int event=parser.next();!(event==XmlPullParser.END_TAG && parser.getDepth()==depth) && event!=XmlPullParser.END_DOCUMENT;event=parser.next()) {
            if(event==XmlPullParser.TEXT || event==XmlPullParser.CDSECT || event==XmlPullParser.ENTITY_REF) {
                String value=parser.getText(); if(value!=null) result.append(value);
            }
        }
        return result.toString();
    }
    static void parse(InputStream input,String timezone,Sink sink) throws IOException {
        try {
            XmlPullParserFactory factory=XmlPullParserFactory.newInstance();
            factory.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
            XmlPullParser parser=factory.newPullParser(); parser.setInput(input,null);
            String channel="",display="",id="",title="",description="";
            long start=0,end=0; int programs=0; boolean root=false;
            for(int event=parser.next();event!=XmlPullParser.END_DOCUMENT;event=parser.next()) {
                if(event==XmlPullParser.START_TAG) {
                    String tag=parser.getName();
                    if(tag.equals("tv")) root=true;
                    else if(tag.equals("channel")) { channel=parser.getAttributeValue(null,"id"); display=""; }
                    else if(tag.equals("display-name") && channel!=null && !channel.isEmpty()) {
                        String text=text(parser); if(display.isEmpty()) display=text; sink.channel(channel,text);
                    } else if(tag.equals("programme")) {
                        id=parser.getAttributeValue(null,"channel"); title=""; description="";
                        start=time(parser.getAttributeValue(null,"start"),timezone); end=time(parser.getAttributeValue(null,"stop"),timezone);
                    } else if(tag.equals("title") && id!=null && !id.isEmpty()) { String text=text(parser); if(title.isEmpty()) title=text; }
                    else if(tag.equals("desc") && id!=null && !id.isEmpty()) { String text=text(parser); if(description.isEmpty()) description=text; }
                } else if(event==XmlPullParser.END_TAG) {
                    if(parser.getName().equals("channel")) { if(display.isEmpty() && channel!=null) sink.channel(channel,channel); channel=""; }
                    else if(parser.getName().equals("programme")) {
                        if(id!=null && !id.isEmpty() && start>0) { sink.program(id,title.isEmpty()?"Program":title,description,start,end); programs++; }
                        id=""; title=""; description="";
                    }
                }
            }
            if(!root) throw new IOException("Źródło nie zwróciło danych XMLTV.");
        } catch(XmlPullParserException e) { throw new IOException("Nieprawidłowy plik XMLTV.",e); }
    }
}
