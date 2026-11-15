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
    static final int BLUE=0xFF00A8E1;
    private final Actions actions;
    private final LinearLayoutManager vertical;
    private List<TvShelfCatalog.Shelf> shelves=Collections.emptyList();
    private final Map<String,Integer> rowPositions=new HashMap<>();
    private String selectedShelf="";
    private int selectedIndex,focusGeneration;
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
    private int heroHeight() { return dp(home()?76:compact()?96:124); }
    private int artworkHeight() { return dp(compact()?144:180); }
    private int cardsHeight() { return dp(compact()?204:240); }
    private TextView text(String value,int size,int color) { return Ui.text(getContext(),value,size,color); }
    private StateListDrawable cardBackground() {
        StateListDrawable states=new StateListDrawable(); GradientDrawable focused=Ui.shape(getContext(),0xFF172B3D,8,0); focused.setStroke(dp(3),0xFFFFFFFF);
        states.addState(new int[]{android.R.attr.state_focused},focused); states.addState(new int[]{},Ui.shape(getContext(),0xFF111D2A,8,0)); return states;
    }
    void submit(List<TvShelfCatalog.Shelf> rows,TvBrowseState anchor,boolean focus) {
        shelves=rows; if(anchor!=null) { selectedShelf=anchor.shelfKey(); selectedIndex=anchor.shelfIndex(); }
        if(shelves.stream().noneMatch(row -> row.key().equals(selectedShelf))) { selectedShelf=shelves.isEmpty()?"":shelves.get(0).key(); selectedIndex=0; }
        adapter.notifyDataSetChanged();
        if(anchor!=null) vertical.scrollToPositionWithOffset(anchor.firstVisible(),anchor.top());
        if(focus && !shelves.isEmpty()) focus(selectedShelf,selectedIndex,anchor);
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
        int row=rowIndex(key),request=++focusGeneration;
        if(anchor==null) vertical.scrollToPositionWithOffset(row+1,row==0?heroHeight():0);
        mountFocus(row,index,anchor,request,0);
    }
    private void mountFocus(int rowIndex,int index,TvBrowseState anchor,int request,int attempt) {
        postDelayed(() -> {
            if(request!=focusGeneration || !isAttachedToWindow() || attempt>20) return;
            ViewHolder holder=findViewHolderForAdapterPosition(rowIndex+1);
            if(!(holder instanceof Row row)) { vertical.scrollToPositionWithOffset(rowIndex+1,rowIndex==0?heroHeight():0); mountFocus(rowIndex,index,anchor,request,attempt+1); return; }
            TvShelfCatalog.Shelf shelf=shelves.get(rowIndex); int target=Math.min(index,shelf.channels().size()+(shelf.more()?1:0)-1);
            if(target<0) return;
            if(anchor!=null && attempt==0) row.layout.scrollToPositionWithOffset(anchor.categoryFirst(),anchor.categoryTop());
            ViewHolder card=row.cards.findViewHolderForAdapterPosition(target);
            if(card==null) { row.layout.scrollToPositionWithOffset(target,0); mountFocus(rowIndex,index,null,request,attempt+1); return; }
            card.itemView.requestFocus();
        },attempt==0?0:32);
    }
    private void describe(Channel channel,TvShelfCatalog.Shelf row) {
        if(hero==null) return;
        hero.title.setText(channel==null?row.title():channel.name());
        hero.subtitle.setText(channel==null?Ui.count(row.total())+" pozycji w kategorii":channel.kind().equals("shortcut")?"Twoje kanały, filmy i seriale w jednym miejscu":channel.kind().equals("series")?"Serial · "+channel.group():"Film · "+channel.group());
        hero.hint.setText(channel!=null && channel.kind().equals("shortcut")?"OK — otwórz":"OK — szczegóły     Menu — opcje");
        ImageLoader.get(getContext()).load(channel==null?"":channel.logo(),hero.image,dp(132),dp(180));
    }
    private final class Hero extends ViewHolder {
        TextView title,subtitle,hint; ImageView image;
        Hero(LinearLayout view) {
            super(view); view.setGravity(Gravity.CENTER_VERTICAL); view.setPadding(dp(12),dp(8),dp(16),dp(12));
            view.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF152C42,0xFF0B1420}));
            LinearLayout copy=new LinearLayout(getContext()); copy.setOrientation(LinearLayout.VERTICAL);
            TextView eyebrow=text("Z TWOJEJ BIBLIOTEKI",12,BLUE); eyebrow.setLetterSpacing(.12f); copy.addView(eyebrow);
            title=text("Wybierz coś na dziś",compact()?22:28,Ui.TEXT); title.setTypeface(null,Typeface.BOLD); title.setMaxLines(compact()?1:2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); copy.addView(title);
            subtitle=text("Filmy i seriale od Twojego dostawcy",15,Ui.MUTED); subtitle.setSingleLine(true); copy.addView(subtitle);
            hint=text("OK — wybierz",13,0xFFBAC8D6); hint.setPadding(0,dp(8),0,0); copy.addView(hint); view.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
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
        LinearLayout view; ImageView image; TextView title,placeholder;
        Card(LinearLayout view) {
            super(view); this.view=view; view.setOrientation(LinearLayout.VERTICAL); view.setPadding(dp(5),dp(5),dp(5),dp(5)); view.setBackground(cardBackground()); view.setFocusable(true); view.setFocusableInTouchMode(true); view.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            FrameLayout art=new FrameLayout(getContext()); art.setBackgroundColor(0xFF192A3A); placeholder=text("",22,0xFF6B8095); placeholder.setGravity(Gravity.CENTER); art.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            image=new ImageView(getContext()); image.setScaleType(ImageView.ScaleType.FIT_CENTER); art.addView(image,new FrameLayout.LayoutParams(-1,-1)); view.addView(art,new LinearLayout.LayoutParams(-1,artworkHeight()));
            title=text("",16,Ui.TEXT); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setPadding(dp(3),dp(5),dp(3),0); view.addView(title,new LinearLayout.LayoutParams(-1,dp(45)));
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
            if(holder instanceof Hero h) { hero=h; h.itemView.getLayoutParams().height=heroHeight(); h.subtitle.setVisibility(home()?View.GONE:View.VISIBLE); h.hint.setVisibility(home()?View.GONE:View.VISIBLE); if(!shelves.isEmpty()) { TvShelfCatalog.Shelf row=shelves.get(rowIndex(selectedShelf)); describe(row.channels().isEmpty()?null:row.channels().get(Math.min(selectedIndex,row.channels().size()-1)),row); } return; }
            Row row=(Row)holder; TvShelfCatalog.Shelf shelf=shelves.get(position-1); row.title.setText(shelf.title()+"   ›");
            boolean shortcuts=!shelf.channels().isEmpty() && shelf.channels().get(0).kind().equals("shortcut"); row.cards.getLayoutParams().height=shortcuts?dp(132):cardsHeight();
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
            card.view.getLayoutParams().width=dp(shortcuts?218:142); card.view.getChildAt(0).getLayoutParams().height=shortcuts?dp(72):artworkHeight();
            String label=channel==null?"Pokaż wszystkie · "+Ui.count(shelf.total()):channel.name(); card.title.setText(label); card.placeholder.setText(channel==null?"→":shortcuts?channel.url().equals("section:tv")?"TV":channel.url().equals("section:movie")?"▶":channel.url().equals("section:series")?"▤":"↩":"▶");
            card.view.setContentDescription(channel==null?"Pokaż wszystkie: "+shelf.title():channel.name()); ImageLoader.get(getContext()).load(channel==null?"":channel.logo(),card.image,dp(132),dp(180));
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
