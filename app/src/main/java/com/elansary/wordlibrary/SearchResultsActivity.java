package com.elansary.wordlibrary;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.concurrent.*;

public class SearchResultsActivity extends AppCompatActivity {
 TextView status;RecyclerView resultsList;ResultsAdapter adapter;Button copyAllButton;Button lengthButton;
 ArrayList<LibraryActivity.WordFile> files;String query;ExecutorService worker=Executors.newSingleThreadExecutor();volatile boolean searching;

 static final class Result{final LibraryActivity.WordFile file;final int paragraph;final String text;Result(LibraryActivity.WordFile file,int paragraph,String text){this.file=file;this.paragraph=paragraph;this.text=text;}}
 ArrayList<Result> results=new ArrayList<>();boolean showFull=true;

 @Override protected void onCreate(Bundle state){
  super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(12),dp(12),dp(12));setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setOrientation(0);header.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=new TextView(this);title.setText("نتائج البحث");title.setTextSize(18);
  lengthButton=new Button(this);lengthButton.setText("كامل");lengthButton.setTextSize(11);lengthButton.setMinWidth(0);lengthButton.setMinimumWidth(0);lengthButton.setPadding(dp(8),0,dp(8),0);lengthButton.setOnClickListener(v->toggleLength());
  copyAllButton=new Button(this);copyAllButton.setText("نسخ الكل");copyAllButton.setTextSize(11);copyAllButton.setMinWidth(0);copyAllButton.setMinimumWidth(0);copyAllButton.setPadding(dp(8),0,dp(8),0);copyAllButton.setOnClickListener(v->copyAll());
  header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(lengthButton,new LinearLayout.LayoutParams(dp(64),dp(36)));header.addView(copyAllButton,new LinearLayout.LayoutParams(dp(84),dp(36)));root.addView(header);
  status=new TextView(this);root.addView(status);
  resultsList=new RecyclerView(this);resultsList.setLayoutManager(new LinearLayoutManager(this));resultsList.setItemAnimator(null);root.addView(resultsList,new LinearLayout.LayoutParams(-1,0,1));
  files=getIntent().getParcelableArrayListExtra("files");query=getIntent().getStringExtra("query");adapter=new ResultsAdapter();resultsList.setAdapter(adapter);startSearch();
 }
 int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 void toggleLength(){showFull=!showFull;lengthButton.setText(showFull?"كامل":"جزئي");if(adapter!=null)adapter.notifyDataSetChanged();}
 void startSearch(){
  if(searching||files==null||files.isEmpty())return;searching=true;status.setText("جار البحث في "+files.size()+" ملف…");results.clear();adapter.notifyDataSetChanged();
  worker.execute(()->{
   String normalized=query.toLowerCase().replaceAll("\\s+"," ").trim();String[] tokens=normalized.split(" ");
   for(LibraryActivity.WordFile file:files){
    if(Thread.currentThread().isInterrupted())return;
    try{
     ArrayList<String> paragraphs=DocxReader.readParagraphs(this,file.uri);
     for(int i=0;i<paragraphs.size();i++){
      String text=paragraphs.get(i);String normalizedText=text.toLowerCase().replaceAll("\\s+"," ").trim();boolean match=true;
      for(String token:tokens)if(!normalizedText.contains(token)){match=false;break;}
      if(match){Result result=new Result(file,i+1,text);runOnUiThread(()->{results.add(result);adapter.notifyItemInserted(results.size()-1);status.setText("نتائج: "+results.size());});}
     }
    }catch(Exception ignored){}
   }
   runOnUiThread(()->{searching=false;if(results.isEmpty())status.setText("لا نتائج");});
  });
 }
 void copyAll(){
  if(results.isEmpty())return;StringBuilder text=new StringBuilder();for(int i=0;i<results.size();i++){Result result=results.get(i);text.append("نتيجة ").append(i+1).append(" — ").append(result.file.name).append(" — فقرة ").append(result.paragraph).append("\n").append(result.text).append("\n\n");}
  copy(text.toString(),"تم نسخ جميع النتائج");
 }
 void copy(String text,String message){
  try{ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard==null)throw new IllegalStateException();clipboard.setPrimaryClip(ClipData.newPlainText("نتائج البحث",text));Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}catch(Exception error){Toast.makeText(this,"تعذر النسخ",Toast.LENGTH_LONG).show();}
 }
 void openInViewer(LibraryActivity.WordFile file){
  try{
   Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(file.uri,"application/vnd.openxmlformats-officedocument.wordprocessingml.document");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(intent);
  }catch(Exception error){Toast.makeText(this,"لا يوجد عارض Word مناسب",Toast.LENGTH_LONG).show();}
 }
 final class ResultsAdapter extends RecyclerView.Adapter<ResultHolder>{
  @Override public ResultHolder onCreateViewHolder(ViewGroup parent,int type){
   LinearLayout card=new LinearLayout(SearchResultsActivity.this);card.setOrientation(1);card.setPadding(dp(12),dp(8),dp(12),dp(12));
   android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFFFFFFFF);bg.setCornerRadius(dp(10));bg.setStroke(dp(1),0xFFBDD1DF);card.setBackground(bg);
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(8);card.setLayoutParams(lp);
   LinearLayout top=new LinearLayout(SearchResultsActivity.this);top.setOrientation(0);top.setGravity(Gravity.CENTER_VERTICAL);
   TextView label=new TextView(SearchResultsActivity.this);label.setTextSize(12);label.setTextColor(0xFF315D7B);
   Button copy=new Button(SearchResultsActivity.this);copy.setText("نسخ");copy.setTextSize(11);copy.setMinWidth(0);copy.setMinimumWidth(0);copy.setPadding(dp(8),0,dp(8),0);
   Button open=new Button(SearchResultsActivity.this);open.setText("عرض");open.setTextSize(11);open.setMinWidth(0);open.setMinimumWidth(0);open.setPadding(dp(8),0,dp(8),0);
   top.addView(label,new LinearLayout.LayoutParams(0,-2,1));top.addView(copy,new LinearLayout.LayoutParams(dp(64),dp(32)));top.addView(open,new LinearLayout.LayoutParams(dp(64),dp(32)));card.addView(top);
   TextView text=new TextView(SearchResultsActivity.this);text.setTextSize(16);text.setTextDirection(View.TEXT_DIRECTION_RTL);card.addView(text,new LinearLayout.LayoutParams(-1,-2));
   return new ResultHolder(card,label,text,copy,open);
  }
  @Override public void onBindViewHolder(ResultHolder holder,int position){
   Result result=results.get(position);holder.label.setText(result.file.name+" — فقرة "+result.paragraph);holder.text.setTextSize(16);
   if(showFull){holder.text.setMaxLines(Integer.MAX_VALUE);holder.text.setEllipsize(null);holder.text.setText(result.text);}
   else{holder.text.setMaxLines(8);holder.text.setEllipsize(TextUtils.TruncateAt.END);holder.text.setText(result.text);}
   holder.copy.setOnClickListener(v->{int p=holder.getBindingAdapterPosition();if(p==RecyclerView.NO_POSITION)return;copy(results.get(p).text,"تم نسخ النتيجة "+(p+1));});
   holder.open.setOnClickListener(v->{int p=holder.getBindingAdapterPosition();if(p==RecyclerView.NO_POSITION)return;openInViewer(results.get(p).file);});
  }
  @Override public int getItemCount(){return results.size();}
 }
 static final class ResultHolder extends RecyclerView.ViewHolder{final TextView label,text;final Button copy,open;ResultHolder(View card,TextView label,TextView text,Button copy,Button open){super(card);this.label=label;this.text=text;this.copy=copy;this.open=open;}}
}
