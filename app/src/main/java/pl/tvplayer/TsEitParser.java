package pl.tvplayer;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Incremental MPEG-TS PSI/SI reader. Reads only PAT, actual SDT and actual EIT, with CRC validation. */
final class TsEitParser {
    private final byte[] packet=new byte[376];
    private int packetSize,transport=-1,patVersion=-1,patLast=-1,sdtTransport=-1;
    private final Map<Integer,Section> sections=new HashMap<>();
    private final Map<Integer,Set<Integer>> pat=new HashMap<>();
    private final Map<Integer,String> names=new HashMap<>();
    private final Map<String,Event> events=new LinkedHashMap<>();
    private final String wanted;
    private boolean changed;
    private int eitSections;
    private record Event(int service,int transport,int network,int event,int version,Guide.Program program) {}
    TsEitParser(String name) { wanted=normalize(name); }
    static String normalize(String value) {
        String s=java.text.Normalizer.normalize(value==null?"":value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
        s=s.replaceFirst("^[|\\s]*(?:(?:pl|pol|uk|us|de|fr|it|es|cz|sk)[|\\s:_-]+)?(?:vip[|\\s:_-]+)?","");
        return s.replaceAll("(?:[\\s|_-]+(?:raw|fhd|hd|uhd|4k|hevc|h265))+$","").replaceAll("[^\\p{L}\\p{N}]","");
    }
    boolean feed(byte[] data,int offset,int length) {
        changed=false;
        for(int i=offset;i<offset+length;i++) {
            if(packetSize==0 && (data[i]&255)!=71) continue;
            packet[packetSize++]=data[i];
            if(packetSize==376) {
                int discard=188;
                if((packet[0]&255)==71 && (packet[188]&255)==71) packet();
                else for(int j=1;j<188;j++) if((packet[j]&255)==71 && (packet[j+188]&255)==71) { discard=j; break; }
                System.arraycopy(packet,discard,packet,0,376-discard); packetSize=376-discard;
            }
        }
        return changed;
    }
    int eitSections() { return eitSections; }
    private void packet() {
        int pid=((packet[1]&31)<<8)|(packet[2]&255),control=(packet[3]>>4)&3;
        if(pid!=0 && pid!=17 && pid!=18) return;
        Section section=sections.computeIfAbsent(pid,k -> new Section(k));
        if((packet[1]&128)!=0 || (packet[3]&192)!=0 || control==0) { section.reset(); return; }
        int position=4;
        if((control&2)!=0) {
            int size=packet[position++]&255;
            if(size>183 || position+size>188) { section.reset(); return; }
            if(size>0 && (packet[position]&128)!=0) section.reset();
            position+=size;
        }
        if((control&1)==0 || position>=188) return;
        int continuity=packet[3]&15;
        if(continuity==section.continuity) return;
        if(section.continuity>=0 && continuity!=((section.continuity+1)&15)) section.size=0;
        section.continuity=continuity;
        boolean start=(packet[1]&64)!=0;
        if(start) {
            int pointer=packet[position++]&255;
            if(position+pointer>188) { section.reset(); return; }
            if(section.size>0) section.append(packet,position,pointer,false);
            section.size=0; position+=pointer;
            section.append(packet,position,188-position,true);
        } else if(section.size>0) section.append(packet,position,188-position,false);
    }
    private final class Section {
        final int pid; final byte[] bytes=new byte[4096]; int size,continuity=-1;
        Section(int pid) { this.pid=pid; }
        void reset() { size=0; continuity=-1; }
        void append(byte[] source,int start,int length,boolean allowNew) {
            for(int i=start;i<start+length;i++) {
                if(size==0 && (!allowNew || (source[i]&255)==255)) return;
                bytes[size++]=source[i];
                if(size>=3) {
                    int total=3+(((bytes[1]&15)<<8)|(bytes[2]&255));
                    if(total<12 || total>4096) { size=0; return; }
                    if(size==total) {
                        if((bytes[1]&128)!=0 && (bytes[5]&1)!=0 && crc(bytes,total)==0) table(pid,bytes,total);
                        size=0; if(!allowNew) return;
                    }
                }
            }
        }
    }
    static int crc(byte[] data,int length) {
        int value=-1;
        for(int i=0;i<length;i++) { value^=(data[i]&255)<<24; for(int j=0;j<8;j++) value=(value<<1)^((value<0)?0x04c11db7:0); }
        return value;
    }
    private static int u16(byte[] s,int p) { return ((s[p]&255)<<8)|(s[p+1]&255); }
    private void table(int pid,byte[] s,int length) {
        int table=s[0]&255,stop=length-4;
        if(pid==0 && table==0) {
            int version=(s[5]>>1)&31,ts=u16(s,3);
            if(ts!=transport || version!=patVersion) { pat.clear(); if(ts!=transport) names.clear(); transport=ts; patVersion=version; }
            Set<Integer> programs=new LinkedHashSet<>();
            for(int p=8;p+4<=stop;p+=4) { int service=u16(s,p); if(service!=0) programs.add(service); }
            patLast=s[7]&255; pat.put(s[6]&255,programs); changed=true;
        } else if(pid==17 && table==66 && length>=15) {
            int ts=u16(s,3); if(transport>=0 && ts!=transport) return;
            if(sdtTransport!=ts) { names.clear(); sdtTransport=ts; }
            for(int p=11;p+5<=stop;) {
                int service=u16(s,p),end=p+5+(((s[p+3]&15)<<8)|(s[p+4]&255));
                if(end>stop) break;
                for(int d=p+5;d+2<=end;) {
                    int tag=s[d]&255,n=s[d+1]&255,next=d+2+n; if(next>end) break;
                    if(tag==72 && n>=3) {
                        int provider=s[d+3]&255,nameAt=d+4+provider;
                        if(nameAt<next) { int count=s[nameAt]&255; if(nameAt+1+count<=next) names.put(service,normalize(DvbText.decode(s,nameAt+1,count))); }
                    }
                    d=next;
                }
                p=end;
            }
            changed=true;
        } else if(pid==18 && (table==78 || (table>=80 && table<=95)) && length>=18) {
            int service=u16(s,3),ts=u16(s,8),network=u16(s,10),version=(s[5]>>1)&31;
            if(transport>=0 && ts!=transport) return;
            eitSections++;
            for(int p=14;p+12<=stop;) {
                int end=p+12+(((s[p+10]&15)<<8)|(s[p+11]&255)); if(end>stop) break;
                long start=time(s,p+2); int duration=duration(s,p+7);
                if(start>0 && duration>0) {
                    Map<String,String> titles=new LinkedHashMap<>(),shorts=new LinkedHashMap<>();
                    Map<String,TreeMap<Integer,String>> extended=new LinkedHashMap<>();
                    for(int d=p+12;d+2<=end;) {
                        int tag=s[d]&255,n=s[d+1]&255,next=d+2+n; if(next>end) break;
                        if(tag==77 && n>=5) {
                            String lang=new String(s,d+2,3,java.nio.charset.StandardCharsets.US_ASCII); int count=s[d+5]&255,desc=d+6+count;
                            if(desc<next && desc+1+(s[desc]&255)<=next) {
                                titles.put(lang,DvbText.decode(s,d+6,count)); shorts.put(lang,DvbText.decode(s,desc+1,s[desc]&255));
                            }
                        } else if(tag==78 && n>=6) {
                            String lang=new String(s,d+3,3,java.nio.charset.StandardCharsets.US_ASCII); int textAt=d+7+(s[d+6]&255);
                            if(textAt<next && textAt+1+(s[textAt]&255)<=next) extended.computeIfAbsent(lang,k -> new TreeMap<>()).put((s[d+2]>>4)&15,DvbText.decode(s,textAt+1,s[textAt]&255));
                        }
                        d=next;
                    }
                    String lang=titles.containsKey("pol")?"pol":titles.containsKey("eng")?"eng":titles.keySet().stream().findFirst().orElse("");
                    String title=titles.getOrDefault(lang,"");
                    if(!title.isEmpty()) {
                        String description=shorts.getOrDefault(lang,"");
                        for(String part:extended.getOrDefault(lang,new TreeMap<>()).values()) description+=(description.isEmpty()?"":"\n")+part;
                        Guide.Program program=new Guide.Program(title,description,start,start+duration,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(start)));
                        int event=u16(s,p); String key=ts+":"+network+":"+service+":"+event+":"+start;
                        Event replacement=new Event(service,ts,network,event,version,program);
                        if(!replacement.equals(events.put(key,replacement))) changed=true;
                        while(events.size()>1500) events.remove(events.keySet().iterator().next());
                    }
                }
                p=end;
            }
        }
    }
    private int service() {
        Set<Integer> all=new LinkedHashSet<>();
        if(patLast<0 || pat.size()!=patLast+1) return -1;
        for(Set<Integer> ids:pat.values()) all.addAll(ids);
        if(all.size()==1) return all.iterator().next();
        int match=-1;
        for(int id:all) if(!wanted.isEmpty() && wanted.equals(names.get(id))) { if(match!=-1) return -1; match=id; }
        return match;
    }
    List<Guide.Program> programs() {
        int service=service(); List<Guide.Program> result=new ArrayList<>();
        if(service<0) return result;
        for(Event e:events.values()) if(e.service()==service && e.transport()==transport) result.add(e.program());
        result.sort(Comparator.comparingLong(Guide.Program::start)); return result;
    }
    private static int bcd(byte value) { int a=(value&255)>>4,b=value&15; return a<=9 && b<=9?a*10+b:-1; }
    private static int duration(byte[] s,int p) { int h=bcd(s[p]),m=bcd(s[p+1]),sec=bcd(s[p+2]); return h>=0 && m>=0 && m<60 && sec>=0 && sec<60?h*3600+m*60+sec:-1; }
    private static long time(byte[] s,int p) {
        if((s[p]&255)==255 && (s[p+1]&255)==255) return 0;
        int h=bcd(s[p+2]),m=bcd(s[p+3]),sec=bcd(s[p+4]);
        return h>=0 && h<24 && m>=0 && m<60 && sec>=0 && sec<60?(u16(s,p)-40587L)*86400+h*3600L+m*60L+sec:0;
    }
}
