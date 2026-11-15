package pl.tvplayer;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

/** Virtualised category shelves with explicit remote focus between horizontal rows. */
final class TvShelfView extends RecyclerView {
    interface Actions {
        void selected(Channel channel,String shelf,int index);
        void open(Channel channel);
        void more(TvShelfCatalog.Shelf shelf);
        void options(Channel channel);
        void navigation();
    }
    static final int BLUE=Ui.ACCENT;
    private final Actions actions;
    private final LinearLayoutManager vertical;
    private List<TvShelfCatalog.Shelf> shelves=Collections.emptyList();
    private final Map<String,Integer> rowPositions=new HashMap<>();
    private String selectedShelf="";
    private int selectedIndex,focusGeneration;
    private boolean mounting;
    /** Last focus-restore steps, read by instrumentation tests when a restore fails. */
    static final java.util.ArrayDeque<String> TRACE=new java.util.ArrayDeque<>();
    static void trace(String line) { synchronized(TRACE) { TRACE.addLast(android.os.SystemClock.uptimeMillis()%100000+" "+line); while(TRACE.size()>160) TRACE.removeFirst(); } }
    private Channel selectedChannel;
    private Hero hero;
    private final ShelfAdapter adapter=new ShelfAdapter();
    TvShelfView(Context context,Actions actions) {
        super(context); this.actions=actions;
        vertical=new LinearLayoutManager(context); setLayoutManager(vertical); setAdapter(adapter);
        setItemAnimator(null); setHasFixedSize(false); setClipToPadding(false); setPadding(0,0,0,dp(12));
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS); setFocusable(false);
    }
    private int dp(int n) { return Ui.dp(getContext(),n); }
    private boolean home() { return !shelves.isEmpty() && !shelves.get(0).channels().isEmpty() && shelves.get(0).channels().get(0).kind().equals("shortcut"); }
    private boolean compact() { return getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<650; }
    // Home shows the three section tiles alone; the title banner only belongs to film and series shelves.
    private int heroHeight() { return home()?0:dp(compact()?96:124); }
    private int tileWidth() { return dp(compact()?232:272); }
    private int tileHeight() { return dp(compact()?150:176); }
    private int artworkHeight() { return dp(compact()?144:180); }
    private int cardsHeight() { return dp(compact()?204:240); }
    private TextView text(String value,int size,int color) { return Ui.text(getContext(),value,size,color); }
    private StateListDrawable cardBackground() {
        StateListDrawable states=new StateListDrawable(); GradientDrawable focused=Ui.shape(getContext(),Ui.SURFACE_HIGH,8,0); focused.setStroke(dp(3),Ui.ACCENT);
        states.addState(new int[]{android.R.attr.state_focused},focused); states.addState(new int[]{},Ui.shape(getContext(),Ui.SURFACE,8,0)); return states;
    }
    void submit(List<TvShelfCatalog.Shelf> rows,TvBrowseState anchor,boolean focus) {
        // Rebinding replaces every card; if a card held (or was about to receive) focus, give it back afterwards.
        boolean keep=hasFocus() || mounting; trace("submit rows="+rows.size()+" focus="+focus+" keep="+keep+" anchor="+(anchor==null?"null":anchor.shelfKey()+"/"+anchor.shelfIndex()));
        shelves=rows; if(anchor!=null) { selectedShelf=anchor.shelfKey(); selectedIndex=anchor.shelfIndex(); }
        if(shelves.stream().noneMatch(row -> row.key().equals(selectedShelf))) { selectedShelf=shelves.isEmpty()?"":shelves.get(0).key(); selectedIndex=0; }
        adapter.notifyDataSetChanged();
        if(anchor!=null) vertical.scrollToPositionWithOffset(anchor.firstVisible(),anchor.top());
        if((focus || keep) && !shelves.isEmpty()) focus(selectedShelf,selectedIndex,anchor);
    }
    Channel selected() { return selectedChannel; }
    String shelf() { return selectedShelf; }
    int index() { return selectedIndex; }
    int firstVisible() { return Math.max(0,vertical.findFirstVisibleItemPosition()); }
    int offset() { View first=vertical.findViewByPosition(firstVisible()); return first==null?0:first.getTop(); }
    int horizontalFirst() { Row row=currentRow(); return row==null?0:Math.max(0,row.layout.findFirstVisibleItemPosition()); }
    int horizontalOffset() { Row row=currentRow(); if(row==null) return 0; View first=row.layout.findViewByPosition(horizontalFirst()); return first==null?0:first.getLeft(); }
    private Row currentRow() { int row=rowIndex(selectedShelf); ViewHolder holder=findViewHolderForAdapterPosition(row+1); return holder instanceof Row?(Row)holder:null; }
    private int rowIndex(String key) { for(int i=0;i<shelves.size();i++) if(shelves.get(i).key().equals(key)) return i; return 0; }
    void focusCurrent() { if(!shelves.isEmpty()) focus(selectedShelf,selectedIndex,null); }
    private void focus(String key,int index,TvBrowseState anchor) {
        if(shelves.isEmpty()) return;
        int row=rowIndex(key),request=++focusGeneration; mounting=true;
        if(anchor==null) vertical.scrollToPositionWithOffset(row+1,row==0?heroHeight():0);
        mountFocus(row,index,anchor,request,0);
    }
    private void mountFocus(int rowIndex,int index,TvBrowseState anchor,int request,int attempt) {
        postDelayed(() -> {
            if(request!=focusGeneration) { trace("mount superseded "+request); return; }
            if(!isAttachedToWindow() || attempt>60) { mounting=false; return; }
            ViewHolder holder=findViewHolderForAdapterPosition(rowIndex+1);
            if(!(holder instanceof Row row)) { vertical.scrollToPositionWithOffset(rowIndex+1,rowIndex==0?heroHeight():0); mountFocus(rowIndex,index,anchor,request,attempt+1); return; }
            TvShelfCatalog.Shelf shelf=shelves.get(rowIndex); int target=Math.min(index,shelf.channels().size()+(shelf.more()?1:0)-1);
            if(target<0) { mounting=false; return; }
            if(anchor!=null && attempt==0) row.layout.scrollToPositionWithOffset(anchor.categoryFirst(),anchor.categoryTop());
            ViewHolder card=row.cards.findViewHolderForAdapterPosition(target);
            trace("mountFocus attempt="+attempt+" row="+rowIndex+" target="+target+" cards="+row.cards.getWidth()+"x"+row.cards.getHeight()+" attached="+row.cards.isAttachedToWindow()+" children="+row.cards.getChildCount()+" items="+(row.cards.getAdapter()==null?-1:row.cards.getAdapter().getItemCount())+" vis="+row.layout.findFirstVisibleItemPosition()+"-"+row.layout.findLastVisibleItemPosition()+" req="+row.cards.isLayoutRequested()+"/"+isLayoutRequested()+" rowTop="+row.itemView.getTop()+" shown="+isShown()+" card="+(card==null?"null":card.itemView.getWidth()+"x"+card.itemView.getHeight()+" vis="+card.itemView.isShown()+" focusable="+card.itemView.isFocusable())+" focus="+findFocus());
            if(card==null || card.itemView.getWidth()==0) {
                // Offset scrolling to the very last card can leave it unlaid when the row is narrower than the screen;
                // alternate with a plain "make visible" scroll, which always lays the target out.
                if(attempt%2==0) row.cards.scrollToPosition(target); else row.layout.scrollToPositionWithOffset(Math.max(0,target-2),0);
                row.cards.requestLayout(); mountFocus(rowIndex,index,null,request,attempt+1); return;
            }
            // A card that is not laid out yet can refuse focus; keep trying instead of leaving focus elsewhere.
            if(!card.itemView.requestFocus() || !card.itemView.hasFocus()) mountFocus(rowIndex,index,null,request,attempt+1); else mounting=false;
        },attempt==0?0:32);
    }
    private void describe(Channel channel,TvShelfCatalog.Shelf row) {
        if(hero==null) return;
        hero.title.setText(channel==null?row.title():channel.name());
        hero.subtitle.setText(channel==null?Ui.count(row.total())+" pozycji w kategorii":channel.kind().equals("shortcut")?shortcutLine(channel.url()):channel.kind().equals("series")?"Serial · "+channel.group():"Film · "+channel.group());
        hero.hint.setText(channel!=null && channel.kind().equals("shortcut")?"OK — otwórz":"OK — szczegóły     Menu — opcje");
        ImageLoader.get(getContext()).load(channel==null?"":channel.logo(),hero.image,dp(132),dp(180));
    }
    private static String shortcutLine(String url) {
        switch(url) {
            case "section:tv": return "Kanały i program TV";
            case "section:movie": return "Kategorie i Moja lista";
            case "section:series": return "Sezony i odcinki";
            default: return "";
        }
    }
    /** Colourful Canal+/Netflix style tiles for the home shortcuts; white focus ring for the remote. */
    private StateListDrawable shortcutBackground(String url) {
        int[] colors=url.equals("section:tv")?new int[]{0xFFFF3D5E,0xFFB0186F}:url.equals("section:movie")?new int[]{0xFF6A5CFF,0xFF35279A}:url.equals("section:series")?new int[]{0xFF00B8D9,0xFF0B6E99}:new int[]{0xFF4A4E58,0xFF2A2D34};
        StateListDrawable states=new StateListDrawable();
        GradientDrawable focused=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors); focused.setCornerRadius(dp(16)); focused.setStroke(dp(4),0xFFFFFFFF);
        // Unfocused tiles are dimmed so the focused one clearly stands out from across the room.
        GradientDrawable normal=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dim(colors[0]),dim(colors[1])}); normal.setCornerRadius(dp(16));
        states.addState(new int[]{android.R.attr.state_focused},focused); states.addState(new int[]{},normal); return states;
    }
    private static int dim(int color) { return android.graphics.Color.rgb(android.graphics.Color.red(color)*7/10,android.graphics.Color.green(color)*7/10,android.graphics.Color.blue(color)*7/10); }
    private final class Hero extends ViewHolder {
        TextView title,subtitle,hint; ImageView image;
        Hero(LinearLayout view) {
            super(view); view.setGravity(Gravity.CENTER_VERTICAL); view.setPadding(dp(12),dp(8),dp(16),dp(12));
            view.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF2A1A20,Ui.BG}));
            LinearLayout copy=new LinearLayout(getContext()); copy.setOrientation(LinearLayout.VERTICAL);
            TextView eyebrow=text("Z TWOJEJ BIBLIOTEKI",12,BLUE); eyebrow.setLetterSpacing(.12f); copy.addView(eyebrow);
            title=text("Wybierz coś na dziś",compact()?22:28,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(compact()?1:2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); copy.addView(title);
            subtitle=text("Filmy i seriale od Twojego dostawcy",15,Ui.MUTED); subtitle.setSingleLine(true); copy.addView(subtitle);
            hint=text("OK — wybierz",13,Ui.MUTED); hint.setPadding(0,dp(8),0,0); copy.addView(hint); view.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            image=new ImageView(getContext()); image.setScaleType(ImageView.ScaleType.FIT_CENTER); view.addView(image,new LinearLayout.LayoutParams(dp(154),-1));
        }
    }
    private final class Row extends ViewHolder {
        TextView title; RecyclerView cards; LinearLayoutManager layout;
        Row(LinearLayout view) {
            super(view); view.setOrientation(LinearLayout.VERTICAL); view.setPadding(0,dp(14),0,dp(6));
            title=text("",20,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); title.setPadding(dp(5),0,0,dp(8)); view.addView(title);
            cards=new RecyclerView(getContext()); layout=new LinearLayoutManager(getContext(),RecyclerView.HORIZONTAL,false); cards.setLayoutManager(layout); cards.setItemAnimator(null); cards.setClipToPadding(false); cards.setPadding(dp(4),dp(4),dp(4),dp(4)); cards.setFocusable(false);
            view.addView(cards,new LinearLayout.LayoutParams(-1,cardsHeight()));
        }
    }
    private final class Card extends ViewHolder {
        LinearLayout view; ImageView image; TextView title,placeholder,caption;
        Card(LinearLayout view) {
            super(view); this.view=view; view.setOrientation(LinearLayout.VERTICAL); view.setPadding(dp(5),dp(5),dp(5),dp(5)); view.setBackground(cardBackground()); view.setFocusable(true); view.setFocusableInTouchMode(true); view.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            FrameLayout art=new FrameLayout(getContext()); art.setBackgroundColor(Ui.SURFACE_HIGH); placeholder=text("",22,Ui.FAINT); placeholder.setGravity(Gravity.CENTER); art.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            image=new ImageView(getContext()); image.setScaleType(ImageView.ScaleType.FIT_CENTER); art.addView(image,new FrameLayout.LayoutParams(-1,-1)); view.addView(art,new LinearLayout.LayoutParams(-1,artworkHeight()));
            title=text("",16,Ui.TEXT); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setPadding(dp(3),dp(5),dp(3),0); view.addView(title,new LinearLayout.LayoutParams(-1,dp(45)));
            caption=text("",14,0xD9FFFFFF); caption.setGravity(Gravity.CENTER_HORIZONTAL); caption.setSingleLine(true); caption.setVisibility(GONE); view.addView(caption,new LinearLayout.LayoutParams(-1,-2));
            RecyclerView.LayoutParams size=new RecyclerView.LayoutParams(dp(142),-1); size.rightMargin=dp(12); view.setLayoutParams(size);
        }
    }
    private final class ShelfAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override public int getItemCount() { return shelves.isEmpty()?0:shelves.size()+1; }
        @Override public int getItemViewType(int position) { return position==0?0:1; }
        @Override public ViewHolder onCreateViewHolder(ViewGroup parent,int type) {
            LinearLayout view=new LinearLayout(getContext()); view.setLayoutParams(new RecyclerView.LayoutParams(-1,type==0?dp(124):-2));
            return type==0?new Hero(view):new Row(view);
        }
        @Override public void onBindViewHolder(ViewHolder holder,int position) {
            if(holder instanceof Hero h) { hero=h; h.itemView.getLayoutParams().height=heroHeight(); h.itemView.setVisibility(home()?INVISIBLE:VISIBLE); h.subtitle.setVisibility(View.VISIBLE); h.hint.setVisibility(View.VISIBLE); if(!shelves.isEmpty()) { TvShelfCatalog.Shelf row=shelves.get(rowIndex(selectedShelf)); describe(row.channels().isEmpty()?null:row.channels().get(Math.min(selectedIndex,row.channels().size()-1)),row); } return; }
            Row row=(Row)holder; TvShelfCatalog.Shelf shelf=shelves.get(position-1); row.title.setText(shelf.title()+"   ›");
            boolean shortcuts=!shelf.channels().isEmpty() && shelf.channels().get(0).kind().equals("shortcut");
            // Section tiles sit centred as one symmetric group; content shelves start at the left edge.
            row.title.setVisibility(shortcuts?GONE:VISIBLE); ((LinearLayout)row.itemView).setGravity(shortcuts?Gravity.CENTER_HORIZONTAL:Gravity.START);
            row.itemView.setPadding(0,dp(shortcuts?(compact()?10:22):14),0,dp(shortcuts?(compact()?12:20):6));
            row.cards.getLayoutParams().width=shortcuts?-2:-1; row.cards.getLayoutParams().height=shortcuts?tileHeight()+dp(8):cardsHeight(); row.cards.requestLayout();
            row.cards.setAdapter(new CardAdapter(shelf,position-1,shortcuts)); row.layout.scrollToPositionWithOffset(rowPositions.getOrDefault(shelf.key(),0),0);
        }
    }
    private final class CardAdapter extends RecyclerView.Adapter<Card> {
        final TvShelfCatalog.Shelf shelf; final int rowIndex; final boolean shortcuts;
        CardAdapter(TvShelfCatalog.Shelf shelf,int rowIndex,boolean shortcuts) { this.shelf=shelf; this.rowIndex=rowIndex; this.shortcuts=shortcuts; }
        @Override public int getItemCount() { return shelf.channels().size()+(shelf.more()?1:0); }
        @Override public Card onCreateViewHolder(ViewGroup parent,int type) { return new Card(new LinearLayout(getContext())); }
        @Override public void onBindViewHolder(Card card,int position) {
            Channel channel=position<shelf.channels().size()?shelf.channels().get(position):null;
            boolean tile=shortcuts && channel!=null; RecyclerView.LayoutParams size=(RecyclerView.LayoutParams)card.view.getLayoutParams();
            size.width=tile?tileWidth():dp(142); size.height=tile?tileHeight():-1; size.leftMargin=tile?dp(12):0; size.rightMargin=dp(12);
            LinearLayout.LayoutParams art=(LinearLayout.LayoutParams)card.view.getChildAt(0).getLayoutParams(); art.height=tile?0:artworkHeight(); art.weight=tile?1:0;
            LinearLayout.LayoutParams label=(LinearLayout.LayoutParams)card.title.getLayoutParams(); label.height=tile?-2:dp(45);
            card.view.setGravity(tile?Gravity.CENTER_HORIZONTAL:Gravity.NO_GRAVITY); card.view.setPadding(dp(tile?12:5),dp(tile?16:5),dp(tile?12:5),dp(tile?14:5));
            ImageLoader.get(getContext()).load(tile || channel==null?"":channel.logo(),card.image,dp(132),dp(180));
            if(tile) {
                card.view.setBackground(shortcutBackground(channel.url())); card.view.getChildAt(0).setBackgroundColor(0);
                card.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE); card.image.setImageDrawable(new TvTileIcon(TvTileIcon.kind(channel.url()),dp(compact()?54:64))); card.placeholder.setText("");
                card.title.setTextSize(compact()?20:22); card.title.setTextColor(0xFFFFFFFF); card.title.setTypeface(null,Typeface.BOLD); card.title.setGravity(Gravity.CENTER_HORIZONTAL); card.title.setMaxLines(1);
                card.caption.setText(shortcutLine(channel.url())); card.caption.setVisibility(VISIBLE);
            } else {
                card.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                card.view.setBackground(cardBackground()); card.view.getChildAt(0).setBackgroundColor(Ui.SURFACE_HIGH);
                card.placeholder.setTextSize(22); card.placeholder.setTextColor(Ui.FAINT); card.placeholder.setText(channel==null?"→":"▶");
                card.title.setTextSize(16); card.title.setTextColor(Ui.TEXT); card.title.setTypeface(null,Typeface.NORMAL); card.title.setGravity(Gravity.START); card.title.setMaxLines(2); card.caption.setVisibility(GONE);
            }
            card.title.setText(channel==null?"Pokaż wszystkie · "+Ui.count(shelf.total()):channel.name());
            card.view.setContentDescription(channel==null?"Pokaż wszystkie: "+shelf.title():channel.name());
            card.view.setOnClickListener(v -> { int current=card.getBindingAdapterPosition(); if(current==RecyclerView.NO_POSITION) return; if(current>=shelf.channels().size()) actions.more(shelf); else actions.open(shelf.channels().get(current)); });
            card.view.setOnLongClickListener(v -> { int current=card.getBindingAdapterPosition(); if(current>=0 && current<shelf.channels().size()) actions.options(shelf.channels().get(current)); return true; });
            card.view.setOnFocusChangeListener((v,focused) -> { int current=card.getBindingAdapterPosition(); if(focused && current>=0) { Channel selected=current<shelf.channels().size()?shelf.channels().get(current):null; selectedShelf=shelf.key(); selectedIndex=current; selectedChannel=selected; rowPositions.put(shelf.key(),current); describe(selected,shelf); actions.selected(selected,shelf.key(),current); } });
            card.view.setOnKeyListener((v,code,event) -> {
                if(code!=KeyEvent.KEYCODE_DPAD_UP && code!=KeyEvent.KEYCODE_DPAD_DOWN) return false;
                int target=rowIndex+(code==KeyEvent.KEYCODE_DPAD_UP?-1:1);
                if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
                    if(target<0) actions.navigation(); else if(target<shelves.size()) focus(shelves.get(target).key(),rowPositions.getOrDefault(shelves.get(target).key(),Math.max(0,card.getBindingAdapterPosition())),null);
                }
                return true;
            });
        }
    }
}
