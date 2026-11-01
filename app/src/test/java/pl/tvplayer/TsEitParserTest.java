package pl.tvplayer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.*;
import java.util.*;

public class TsEitParserTest {
    private static final long START=1793570400L;
    private TsEitParser read(byte[] data) { TsEitParser p=new TsEitParser("PL-VIP| TVN RAW"); for(int at=0;at<data.length;at+=17) p.feed(data,at,Math.min(17,data.length-at)); return p; }
    private byte[] stream(byte[] pat,byte[] eit) { return EitFixture.concat(EitFixture.packets(0,pat),EitFixture.packets(18,eit),EitFixture.padding(),EitFixture.padding()); }
    @Test public void mpegCrcMatchesIndependentCheckVector() { byte[] s="123456789".getBytes(StandardCharsets.US_ASCII); assertEquals(0x0376e6e7,TsEitParser.crc(s,s.length)); }
    @Test public void readsCurrentAndNextInArbitraryChunks() {
        TsEitParser p=read(EitFixture.stream(START)); List<Guide.Program> result=p.programs();
        assertEquals(2,result.size()); assertEquals("Wiadomości ze strumienia",result.get(0).title()); assertEquals("Opis audycji",result.get(0).description());
        assertEquals(START,result.get(0).start()); assertEquals(START+3600,result.get(0).end()); assertTrue(result.get(0).current(START+10)); assertEquals("Następny program",result.get(1).title());
    }
    @Test public void resynchronizesAfterLeadingJunkAndFalseSync() {
        byte[] junk=new byte[133]; Arrays.fill(junk,(byte)3); junk[7]=71;
        assertEquals(2,read(EitFixture.concat(junk,EitFixture.stream(START))).programs().size());
    }
    @Test public void rejectsCorruptCrc() {
        byte[] eit=EitFixture.eit(101,START,"Test"); eit[20]^=1;
        assertTrue(read(stream(EitFixture.pat(101),eit)).programs().isEmpty());
    }
    @Test public void neverUsesAnotherServicesProgramme() {
        assertTrue(read(stream(EitFixture.pat(101),EitFixture.eit(202,START,"Wrong channel"))).programs().isEmpty());
    }
    @Test public void ambiguousMultiplexDoesNotGuessFirstService() {
        assertTrue(read(stream(EitFixture.pat(101,202),EitFixture.eit(101,START,"Wrong channel"))).programs().isEmpty());
    }
    @Test public void sdtSelectsNamedServiceInMultiplex() {
        byte[] data=EitFixture.concat(EitFixture.packets(0,EitFixture.pat(101,202)),EitFixture.packets(17,EitFixture.sdt(202,"TVN HD")),EitFixture.packets(18,EitFixture.eit(202,START,"Fakty")),EitFixture.padding(),EitFixture.padding());
        assertEquals("Fakty",read(data).programs().get(0).title());
    }
    @Test public void reassemblesMultiPacketSections() {
        String description="Opis ".repeat(36);
        byte[] eit=EitFixture.section(78,101,EitFixture.concat(new byte[]{0,1,0,1,0,78},EitFixture.event(1,START,"Film",description)));
        assertEquals(description.trim(),read(stream(EitFixture.pat(101),eit)).programs().get(0).description());
    }
    @Test public void missingContinuityPacketCannotProduceGuide() {
        String description="Opis ".repeat(38);
        byte[] eit=EitFixture.section(78,101,EitFixture.concat(new byte[]{0,1,0,1,0,78},EitFixture.event(1,START,"Film",description),EitFixture.event(2,START+3600,"Serial",description)));
        byte[] packets=EitFixture.packets(18,eit);
        byte[] truncated=EitFixture.concat(Arrays.copyOfRange(packets,0,188),Arrays.copyOfRange(packets,376,packets.length));
        assertTrue(read(EitFixture.concat(EitFixture.packets(0,EitFixture.pat(101)),truncated,EitFixture.padding(),EitFixture.padding())).programs().isEmpty());
    }
    @Test public void ignoresOtherTransportTables() {
        byte[] eit=EitFixture.eit(101,START,"Wrong transport"); eit[0]=79; int crc=TsEitParser.crc(eit,eit.length-4); for(int i=0;i<4;i++) eit[eit.length-4+i]=(byte)(crc>>(24-8*i));
        assertTrue(read(stream(EitFixture.pat(101),eit)).programs().isEmpty());
    }
    @Test public void understandsPolishCharacterTables() {
        byte[] utf=EitFixture.text("Zażółć gęślą jaźń"); assertEquals("Zażółć gęślą jaźń",DvbText.decode(utf,0,utf.length));
        byte[] iso=EitFixture.concat(new byte[]{16,0,2},"Zażółć".getBytes(Charset.forName("ISO-8859-2"))); assertEquals("Zażółć",DvbText.decode(iso,0,iso.length));
        byte[] defaultText=new byte[]{(byte)0xc2,'s',(byte)0xce,'a',(byte)0xf8}; assertEquals("śął",DvbText.decode(defaultText,0,defaultText.length));
    }
    @Test public void rejectsUnboundEitWithoutPat() {
        assertTrue(read(EitFixture.concat(EitFixture.packets(18,EitFixture.eit(101,START,"Unbound")),EitFixture.padding(),EitFixture.padding())).programs().isEmpty());
    }
    @Test public void readsMultipleSectionsInOnePayload() {
        byte[] first=EitFixture.section(78,101,EitFixture.concat(new byte[]{0,1,0,1,0,78},EitFixture.event(1,START,"One","")));
        byte[] second=EitFixture.section(78,101,EitFixture.concat(new byte[]{0,1,0,1,0,78},EitFixture.event(2,START+3600,"Two","")));
        assertEquals(2,read(stream(EitFixture.pat(101),EitFixture.concat(first,second))).programs().size());
    }
}
