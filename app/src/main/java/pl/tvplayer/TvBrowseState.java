package pl.tvplayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** UI location only. Stream URLs and credentials are never saved in preferences. */
record TvBrowseState(boolean home,String section,String group,String query,boolean favorites,String folderId,String folderTitle,
                     String selectedId,String selectedName,int selectedIndex,int firstVisible,int top,int categoryFirst,int categoryTop,
                     boolean contentFocus,boolean grid,String shelfKey,int shelfIndex,TvBrowseState parent) {
    TvBrowseState {
        section=Arrays.asList("tv","movie","series").contains(section)?section:"tv";
        group=clean(group); query=clean(query); folderId=clean(folderId); folderTitle=clean(folderTitle);
        if(!folderId.startsWith("local-series:")) { folderId=""; folderTitle=""; }
        selectedId=clean(selectedId); selectedName=clean(selectedName); shelfKey=clean(shelfKey);
        selectedIndex=Math.max(0,selectedIndex); firstVisible=Math.max(0,firstVisible); categoryFirst=Math.max(0,categoryFirst); shelfIndex=Math.max(0,shelfIndex);
    }
    private static String clean(String value) { return value==null?"":value; }
    static String id(Channel channel) {
        if(channel==null) return "";
        try { byte[] hash=MessageDigest.getInstance("SHA-256").digest(channel.url().getBytes(StandardCharsets.UTF_8)); char[] chars=new char[hash.length*2],digits="0123456789abcdef".toCharArray(); for(int i=0;i<hash.length;i++) { chars[i*2]=digits[(hash[i]&255)>>>4]; chars[i*2+1]=digits[hash[i]&15]; } return new String(chars); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    int locate(List<Channel> channels) {
        if(channels.isEmpty()) return 0;
        int index=Math.min(selectedIndex,channels.size()-1);
        if(id(channels.get(index)).equals(selectedId)) return index;
        for(int i=0;i<channels.size();i++) if(channels.get(i).name().equals(selectedName) && id(channels.get(i)).equals(selectedId)) return i;
        return index;
    }
}
