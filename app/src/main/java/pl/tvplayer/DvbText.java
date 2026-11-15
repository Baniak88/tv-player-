package pl.tvplayer;

import java.nio.charset.*;
import java.text.Normalizer;

/** ETSI EN 300 468 Annex A: explicitly signalled character tables, otherwise ISO 6937. */
final class DvbText {
    private static final String HIGH="\u00a0\u00a1\u00a2\u00a3\u20ac\u00a5\ufffd\u00a7\u00a4\u2018\u201c\u00ab\u2190\u2191\u2192\u2193\u00b0\u00b1\u00b2\u00b3\u00d7\u00b5\u00b6\u00b7\u00f7\u2019\u201d\u00bb\u00bc\u00bd\u00be\u00bf\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\u2014\u00b9\u00ae\u00a9\u2122\u266a\u00ac\u00a6\ufffd\ufffd\ufffd\ufffd\u215b\u215c\u215d\u215e\u2126\u00c6\u00d0\u00aa\u0126\ufffd\u0132\u013f\u0141\u00d8\u0152\u00ba\u00de\u0166\u014a\u0149\u0138\u00e6\u0111\u00f0\u0127\u0131\u0133\u0140\u0142\u00f8\u0153\u00df\u00fe\u0167\u014b\u00ad";
    static String decode(byte[] data,int start,int length) {
        if(length<=0 || start<0 || start+length>data.length) return "";
        int selector=data[start]&255,skip=0; String charset=null;
        if(selector>=1 && selector<=11 && selector!=8) { charset="ISO-8859-"+(selector+4); skip=1; }
        else if(selector==16 && length>=3 && data[start+1]==0) { charset="ISO-8859-"+(data[start+2]&255); skip=3; }
        else if(selector==17) { charset="UTF-16BE"; skip=1; }
        else if(selector==18) { charset="EUC-KR"; skip=1; }
        else if(selector==19) { charset="GB2312"; skip=1; }
        else if(selector==20) { charset="Big5"; skip=1; }
        else if(selector==21) { charset="UTF-8"; skip=1; }
        else if(selector<32) return ""; // Reserved/unsupported selectors must not be guessed.
        String value;
        if(charset!=null) {
            try { value=new String(data,start+skip,length-skip,Charset.forName(charset)); }
            catch(IllegalArgumentException e) { return ""; }
        } else {
            StringBuilder text=new StringBuilder();
            for(int i=start;i<start+length;i++) {
                int c=data[i]&255;
                String marks="\u0300\u0301\u0302\u0303\u0304\u0306\u0307\u0308\u0000\u030a\u0327\u0000\u030b\u0328\u030c";
                if(c>=193 && c<=207 && marks.charAt(c-193)!=0 && i+1<start+length) {
                    int base=data[++i]&255; text.append(base>=160?HIGH.charAt(base-160):(char)base).append(marks.charAt(c-193));
                } else text.append(c>=160?HIGH.charAt(c-160):(char)c);
            }
            value=Normalizer.normalize(text,Normalizer.Form.NFC);
        }
        return value.replace('\u008a','\n').replaceAll("[\\p{Cc}&&[^\\n\\t]]","").trim();
    }
}
