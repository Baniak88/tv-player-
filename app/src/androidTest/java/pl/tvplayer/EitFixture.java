package pl.tvplayer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

final class EitFixture {
    static byte[] concat(byte[]... arrays) { ByteArrayOutputStream out=new ByteArrayOutputStream(); for(byte[] a:arrays) out.write(a,0,a.length); return out.toByteArray(); }
    static void word(ByteArrayOutputStream out,int n) { out.write(n>>8); out.write(n); }
    static byte[] section(int table,int id,byte[] body) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); int length=5+body.length+4;
        out.write(table); out.write(0xb0|(length>>8)); out.write(length); word(out,id); out.write(0xc1); out.write(0); out.write(0); out.write(body,0,body.length);
        byte[] data=out.toByteArray(); int crc=TsEitParser.crc(data,data.length);
        out.write(crc>>24); out.write(crc>>16); out.write(crc>>8); out.write(crc); return out.toByteArray();
    }
    static byte[] pat(int... services) {
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        for(int service:services) { word(body,service); word(body,0xe100+service); }
        return section(0,1,body.toByteArray());
    }
    static byte[] text(String s) { return concat(new byte[]{21},s.getBytes(StandardCharsets.UTF_8)); }
    static byte[] descriptor(String title,String description) {
        byte[] name=text(title),desc=text(description); ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(77); out.write(5+name.length+desc.length); byte[] language="pol".getBytes(StandardCharsets.US_ASCII); out.write(language,0,3);
        out.write(name.length); out.write(name,0,name.length); out.write(desc.length); out.write(desc,0,desc.length); return out.toByteArray();
    }
    static int bcd(int n) { return ((n/10)<<4)|(n%10); }
    static byte[] event(int id,long start,String title,String description) {
        byte[] descriptors=descriptor(title,description); ByteArrayOutputStream out=new ByteArrayOutputStream(); word(out,id);
        ZonedDateTime date=Instant.ofEpochSecond(start).atZone(ZoneOffset.UTC); word(out,(int)(Math.floorDiv(start,86400)+40587));
        out.write(bcd(date.getHour())); out.write(bcd(date.getMinute())); out.write(bcd(date.getSecond()));
        out.write(1); out.write(0); out.write(0); out.write(0x80|(descriptors.length>>8)); out.write(descriptors.length); out.write(descriptors,0,descriptors.length); return out.toByteArray();
    }
    static byte[] eit(int service,long start,String title) {
        return section(78,service,concat(new byte[]{0,1,0,1,0,78},event(1,start,title,"Opis audycji"),event(2,start+3600,"Następny program","Opis kolejny")));
    }
    static byte[] sdt(int service,String name) {
        byte[] n=text(name),descriptor=concat(new byte[]{72,(byte)(3+n.length),1,0,(byte)n.length},n);
        return section(66,1,concat(new byte[]{0,1,(byte)255,(byte)(service>>8),(byte)service,3,(byte)0x80,(byte)descriptor.length},descriptor));
    }
    static byte[] packets(int pid,byte[] section) {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); int at=0,cc=0;
        while(at<section.length) {
            byte[] packet=new byte[188]; Arrays.fill(packet,(byte)255); packet[0]=71; packet[1]=(byte)((pid>>8)|(at==0?64:0)); packet[2]=(byte)pid; packet[3]=(byte)(16|(cc++&15)); int pos=4;
            if(at==0) packet[pos++]=0;
            int count=Math.min(188-pos,section.length-at); System.arraycopy(section,at,packet,pos,count); at+=count; out.write(packet,0,188);
        }
        return out.toByteArray();
    }
    static byte[] padding() { byte[] packet=new byte[188]; Arrays.fill(packet,(byte)255); packet[0]=71; packet[1]=31; packet[2]=(byte)255; packet[3]=16; return packet; }
    static byte[] stream(long start) { return concat(packets(0,pat(101)),packets(18,eit(101,start,"Wiadomości ze strumienia")),padding(),padding()); }
}
