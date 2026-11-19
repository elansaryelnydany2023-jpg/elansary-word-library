package com.elansary.wordlibrary;
import android.content.*;
import android.os.Bundle;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class SearchResultsActivity extends AppCompatActivity {
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 final ArrayList<Hit> hits=new ArrayList<>();final ArrayList<Row> rows=new ArrayList<>();final Set<String> folded=new HashSet<>();
 final int[] colors={0xFFFFE082,0xFF80DEEA,0xFFF8BBD0,0xFFC5E1A5,0xFFD1C4E9};
 ArrayList<LibraryActivity.WordFile> files;String query="";String[] terms;TextView status,document,title;ViewFlipper pages;RecyclerView list;ScrollView scroll;Adapter adapter;
 Hit active;boolean full=true,dead;int generation;String loaded="";ArrayList<String> paragraphs;int[] starts;
 static class Hit {final LibraryActivity.WordFile file;final int paragraph;final String text,path;Hit(LibraryActivity.WordFile f,int p,String t,String folder){file=f;paragraph=p;text=t;path=folder;}}
 static class Row {String key,label;int type,depth;Hit hit;Row(String k,String s,int t,int d,Hit h){key=k;label=s;type=t;depth=d;hit=h;}}
 static class Node {String key,name;LinkedHashMap<String,Node> folders=new LinkedHashMap<>();LinkedHashMap<String,ArrayList<Hit>> groups=new LinkedHashMap<>();Node(String k,String n){key=k;name=n;}int count(){int n=0;for(Node c:folders.values())n+=c.count();for(ArrayList<Hit> h:groups.values())n+=h.size();return n;}}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
 Button button(String text,LinearLayout parent,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(12);parent.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->action.run());return b;}
 @Override public void onCreate(Bundle state){super.onCreate(state);
  query=getIntent().getStringExtra("query");if(query==null)query="";terms=query.trim().isEmpty()?new String[0]:query.trim().split("\\s+");
  files=getIntent().getParcelableArrayListExtra("files");
  LinearLayout root=column();root.setPadding(dp(8),dp(8),dp(8),0);setContentView(root);
  LinearLayout nav=new LinearLayout(this);button("النتائج",nav,()->showResults());button("العارض",nav,()->{if(active!=null)pages.setDisplayedChild(1);else Toast.makeText(this,"اختر نتيجة أولًا",Toast.LENGTH_SHORT).show();});root.addView(nav);
  status=new TextView(this);root.addView(status);pages=new ViewFlipper(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout results=column(),tools=new LinearLayout(this);button("طي الكل",tools,()->{for(Row r:rows)if(r.type!=2)folded.add(r.key);rebuild();});button("فرد الكل",tools,()->{folded.clear();rebuild();});button("كامل/مختصر",tools,()->{full=!full;adapter.notifyDataSetChanged();});button("نسخ الكل",tools,()->copyAll());results.addView(tools);
  list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new Adapter();list.setAdapter(adapter);list.setItemAnimator(null);results.addView(list,new LinearLayout.LayoutParams(-1,0,1));pages.addView(results);
  LinearLayout viewer=column();title=new TextView(this);title.setTextSize(17);viewer.addView(title);TextView note=new TextView(this);note.setText("عارض نصي تجريبي — لا يحافظ على تنسيق صفحات Word");viewer.addView(note);
  LinearLayout movement=new LinearLayout(this);button("السابقة",movement,()->move(-1));button("التالية",movement,()->move(1));button("نسخ النتيجة",movement,()->{if(active!=null)copy(active.text);});viewer.addView(movement);
  scroll=new ScrollView(this);document=new TextView(this);document.setTextSize(19);document.setTextDirection(View.TEXT_DIRECTION_RTL);document.setTextIsSelectable(true);document.setPadding(dp(12),dp(12),dp(12),dp(30));scroll.addView(document);viewer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));pages.addView(viewer);
  search();
 }
 static String normalize(String s){StringBuilder out=new StringBuilder();for(int i=0;i<s.length();i++){char c=fold(s.charAt(i));if(c!=0)out.append(c);}return out.toString();}
 static char fold(char c){if((c>=0x064B&&c<=0x065F)||c==0x0670||c==0x0640)return 0;if(c=='أ'||c=='إ'||c=='آ'||c=='ٱ')return 'ا';if(c=='ى'||c=='ی')return 'ي';return Character.toLowerCase(c);}
 int firstMatch(String text,String term,int from){return normalize(text).indexOf(normalize(term),from);}
 SpannableString highlight(String text){SpannableString span=new SpannableString(text);StringBuilder norm=new StringBuilder();ArrayList<Integer> map=new ArrayList<>();for(int i=0;i<text.length();i++){char c=fold(text.charAt(i));if(c!=0){norm.append(c);map.add(i);}}
  String normalized=norm.toString();for(int t=0;t<terms.length;t++){String token=normalize(terms[t]);if(token.isEmpty())continue;int p=0;while((p=normalized.indexOf(token,p))>=0){int end=p+token.length();span.setSpan(new BackgroundColorSpan(colors[t%colors.length]),map.get(p),map.get(end-1)+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);p=end;}}return span;
 }
 void search(){if(files==null||terms.length==0){status.setText("لا ملفات أو كلمات بحث");return;}status.setText("جار البحث…");
  worker.execute(()->{ArrayList<Hit> found=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();
   for(LibraryActivity.WordFile file:files){if(Thread.currentThread().isInterrupted())return;try{
    ArrayList<String> ps=DocxReader.readParagraphs(this,file.uri);String path=getSharedPreferences("file_paths",0).getString(file.uri.toString(),"ملفات مختارة");
    for(int i=0;i<ps.size();i++){String n=normalize(ps.get(i));boolean ok=true;for(String term:terms){String token=normalize(term);if(!token.isEmpty()&&!n.contains(token)){ok=false;break;}}if(ok)found.add(new Hit(file,i,ps.get(i),path));}
   }catch(Exception e){errors.add(file.name+": "+e.getClass().getSimpleName()+" — "+e.getMessage());}}
   runOnUiThread(()->{if(dead)return;hits.addAll(found);rebuild();status.setText("نتائج: "+hits.size()+" — ملفات تعذر قراءتها: "+errors.size());if(!errors.isEmpty())new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("ملفات لم تُقرأ — ليست بلا نتائج").setMessage(TextUtils.join("\n\n",errors)).setPositiveButton("نسخ التفاصيل",(d,w)->copy(TextUtils.join("\n\n",errors))).setNegativeButton("إغلاق",null).show();});
  });
 }
 void rebuild(){Node root=new Node("root","");for(Hit h:hits){Node n=root;String key="folder";for(String part:h.path.split("/")){if(part.isEmpty())continue;key+="/"+part;Node child=n.folders.get(part);if(child==null){child=new Node(key,part);n.folders.put(part,child);}n=child;}String uri=h.file.uri.toString();ArrayList<Hit> group=n.groups.get(uri);if(group==null){group=new ArrayList<>();n.groups.put(uri,group);}group.add(h);}
  rows.clear();append(root,0);adapter.notifyDataSetChanged();
 }
 void append(Node n,int depth){for(Node child:n.folders.values()){rows.add(new Row(child.key,child.name+" ("+child.count()+")",0,depth,null));if(!folded.contains(child.key))append(child,depth+1);}
  for(Map.Entry<String,ArrayList<Hit>> entry:n.groups.entrySet()){ArrayList<Hit> group=entry.getValue();String key="file:"+entry.getKey();Hit first=group.get(0);rows.add(new Row(key,first.file.name+" ("+group.size()+")",1,depth,first));if(!folded.contains(key))for(Hit h:group)rows.add(new Row(key+":"+h.paragraph,"فقرة "+(h.paragraph+1),2,depth+1,h));}
 }
 void open(Hit hit){active=hit;adapter.notifyDataSetChanged();pages.setDisplayedChild(1);title.setText(hit.file.name+" — فقرة "+(hit.paragraph+1));int request=++generation;
  if(hit.file.uri.toString().equals(loaded)&&paragraphs!=null){render(hit);return;}
  document.setText("جار فتح المستند…");worker.execute(()->{try{ArrayList<String> ps=DocxReader.readParagraphs(this,hit.file.uri);runOnUiThread(()->{if(dead||request!=generation)return;paragraphs=ps;loaded=hit.file.uri.toString();render(hit);});}catch(Exception e){runOnUiThread(()->{if(!dead&&request==generation)document.setText("تعذر فتح "+hit.file.name+"\n"+e);});}});
 }
 void render(Hit hit){StringBuilder all=new StringBuilder();starts=new int[paragraphs.size()];for(int i=0;i<paragraphs.size();i++){starts[i]=all.length();all.append(paragraphs.get(i)).append("\n\n");}SpannableString span=highlight(all.toString());
  if(hit.paragraph>=starts.length){document.setText("تغير المستند؛ أعد البحث لتحديد الموضع الصحيح");return;}
  int start=starts[hit.paragraph],end=start+paragraphs.get(hit.paragraph).length();
  span.setSpan(new android.text.style.LeadingMarginSpan.Standard(dp(10)),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  span.setSpan(new android.text.style.LineBackgroundSpan(){public void drawBackground(android.graphics.Canvas canvas,android.graphics.Paint paint,int left,int right,int top,int baseline,int bottom,CharSequence text,int a,int b,int line){int old=paint.getColor();paint.setColor(0xFFE0F2F1);canvas.drawRect(left,top,right,bottom,paint);paint.setColor(old);}},start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  document.setText(span);int target=start;for(String term:terms){int p=firstMatch(paragraphs.get(hit.paragraph),term,0);if(p>=0){int normalizedIndex=0;String text=paragraphs.get(hit.paragraph);for(int i=0;i<text.length();i++){if(fold(text.charAt(i))!=0){if(normalizedIndex==p){target=start+i;break;}normalizedIndex++;}}break;}}
  final int offset=target;document.post(()->{if(dead||active!=hit||document.getLayout()==null)return;int line=document.getLayout().getLineForOffset(Math.min(offset,document.length()));scroll.scrollTo(0,Math.max(0,document.getLayout().getLineTop(line)+document.getPaddingTop()-dp(24)));});
 }
 void move(int delta){if(active==null)return;int p=hits.indexOf(active)+delta;if(p>=0&&p<hits.size())open(hits.get(p));else Toast.makeText(this,"لا توجد نتيجة أخرى في هذا الاتجاه",Toast.LENGTH_SHORT).show();}
 void showResults(){pages.setDisplayedChild(0);if(active==null)return;for(int i=0;i<rows.size();i++)if(rows.get(i).type==2&&rows.get(i).hit==active){list.scrollToPosition(i);return;}}
 void copy(String text){ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cb!=null){cb.setPrimaryClip(ClipData.newPlainText("الأنصاري",text));Toast.makeText(this,"تم النسخ",Toast.LENGTH_SHORT).show();}}
 void copyAll(){StringBuilder out=new StringBuilder();for(Hit h:hits)out.append(h.file.name).append(" — فقرة ").append(h.paragraph+1).append('\n').append(h.text).append("\n\n");copy(out.toString());}
 @Override public void onBackPressed(){if(pages.getDisplayedChild()==1)showResults();else super.onBackPressed();}
 @Override protected void onDestroy(){dead=true;generation++;worker.shutdownNow();super.onDestroy();}
 class Adapter extends RecyclerView.Adapter<Holder>{
  public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout card=column();TextView label=new TextView(SearchResultsActivity.this),text=new TextView(SearchResultsActivity.this);label.setTextSize(16);text.setTextSize(17);text.setTextDirection(View.TEXT_DIRECTION_RTL);card.addView(label);card.addView(text);RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(5);card.setLayoutParams(lp);return new Holder(card,label,text);}
  public void onBindViewHolder(Holder h,int position){Row r=rows.get(position);boolean current=r.hit!=null&&active!=null&&r.hit.file.uri.equals(active.file.uri);int bg=r.type==0?0xFFDCEAF9:r.type==1?0xFFE8DEF8:0xFFFFFFFF;if(r.type==1&&current)bg=0xFFB2DFDB;if(r.type==2&&r.hit==active)bg=0xFFFFD180;
   h.itemView.setBackgroundColor(bg);h.itemView.setPadding(dp(10+r.depth*12),dp(12),dp(10),dp(12));h.label.setText((r.type==2?"":folded.contains(r.key)?"+ ":"− ")+r.label+(r.type==1&&current?" — مفتوح":r.type==2&&r.hit==active?" — النتيجة الحالية":""));h.text.setVisibility(r.type==2?View.VISIBLE:View.GONE);
   if(r.type==2){h.text.setText(highlight(r.hit.text));h.text.setMaxLines(full?Integer.MAX_VALUE:6);h.text.setEllipsize(full?null:TextUtils.TruncateAt.END);}
   h.itemView.setOnClickListener(v->{int p=h.getBindingAdapterPosition();if(p==RecyclerView.NO_POSITION)return;Row item=rows.get(p);if(item.type==2)open(item.hit);else{if(!folded.add(item.key))folded.remove(item.key);rebuild();}});
   h.itemView.setOnLongClickListener(v->{if(r.hit!=null){copy(r.type==2?r.hit.text:r.hit.file.name);return true;}return false;});
  }
  public int getItemCount(){return rows.size();}
 }
 static class Holder extends RecyclerView.ViewHolder{TextView label,text;Holder(View v,TextView a,TextView b){super(v);label=a;text=b;}}
}
