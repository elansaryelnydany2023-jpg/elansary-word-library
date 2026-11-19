package com.elansary.wordlibrary;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.*;

public class SearchResultsActivity extends AppCompatActivity {
 TextView status;RecyclerView resultsList;TreeAdapter adapter;Button copyAllButton;Button lengthButton;Button collapseFiles;Button expandFiles;Button collapseResults;Button expandResults;Button syncButton;Button backButton;
 ArrayList<LibraryActivity.WordFile> files;String query;ExecutorService worker=Executors.newSingleThreadExecutor();volatile boolean searching;

 static final class Result{final String fileName;final int paragraph;final String text;Result(String fileName,int paragraph,String text){this.fileName=fileName;this.paragraph=paragraph;this.text=text;}}
 static final class FileNode{final String name;final ArrayList<Result> results=new ArrayList<>();boolean expanded=true;FileNode(String name){this.name=name;}}
 ArrayList<FileNode> nodes=new ArrayList<>();ArrayList<Result> results=new ArrayList<>();boolean showFull=true;int activeResult=-1;

 @Override protected void onCreate(Bundle state){
  super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(12),dp(12),dp(12));setContentView(root);
  LinearLayout header=new LinearLayout(this);header.setOrientation(0);header.setGravity(Gravity.CENTER_VERTICAL);
  TextView title=new TextView(this);title.setText("شجرة النتائج");title.setTextSize(18);
  lengthButton=new Button(this);lengthButton.setText("كامل");lengthButton.setTextSize(11);lengthButton.setMinWidth(0);lengthButton.setMinimumWidth(0);lengthButton.setPadding(dp(8),0,dp(8),0);lengthButton.setOnClickListener(v->toggleLength());
  copyAllButton=new Button(this);copyAllButton.setText("نسخ الكل");copyAllButton.setTextSize(11);copyAllButton.setMinWidth(0);copyAllButton.setMinimumWidth(0);copyAllButton.setPadding(dp(8),0,dp(8),0);copyAllButton.setOnClickListener(v->copyAll());
  header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(lengthButton,new LinearLayout.LayoutParams(dp(64),dp(36)));header.addView(copyAllButton,new LinearLayout.LayoutParams(dp(84),dp(36)));root.addView(header);
  LinearLayout controls=new LinearLayout(this);controls.setOrientation(0);controls.setPadding(0,dp(6),0,dp(6));
  collapseFiles=new Button(this);collapseFiles.setText("طي الملفات");collapseFiles.setTextSize(11);collapseFiles.setOnClickListener(v->setFiles(false));
  expandFiles=new Button(this);expandFiles.setText("فرد الملفات");expandFiles.setTextSize(11);expandFiles.setOnClickListener(v->setFiles(true));
  collapseResults=new Button(this);collapseResults.setText("طي النتائج");collapseResults.setTextSize(11);collapseResults.setOnClickListener(v->setResults(false));
  expandResults=new Button(this);expandResults.setText("فرد النتائج");expandResults.setTextSize(11);expandResults.setOnClickListener(v->setResults(true));
  controls.addView(collapseFiles,new LinearLayout.LayoutParams(0,-2,1));controls.addView(expandFiles,new LinearLayout.LayoutParams(0,-2,1));controls.addView(collapseResults,new LinearLayout.LayoutParams(0,-2,1));controls.addView(expandResults,new LinearLayout.LayoutParams(0,-2,1));root.addView(controls);
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);actions.setPadding(0,dp(6),0,dp(6));
  syncButton=new Button(this);syncButton.setText("مزامنة");syncButton.setTextSize(11);syncButton.setOnClickListener(v->syncActive());
  backButton=new Button(this);backButton.setText("عودة للمستكشف");backButton.setTextSize(11);backButton.setOnClickListener(v->finish());
  actions.addView(syncButton,new LinearLayout.LayoutParams(0,-2,1));actions.addView(backButton,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
  status=new TextView(this);root.addView(status);
  resultsList=new RecyclerView(this);resultsList.setLayoutManager(new LinearLayoutManager(this));resultsList.setItemAnimator(null);root.addView(resultsList,new LinearLayout.LayoutParams(-1,0,1));
  files=getIntent().getParcelableArrayListExtra("files");query=getIntent().getStringExtra("query");adapter=new TreeAdapter();resultsList.setAdapter(adapter);startSearch();
 }
 int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 void toggleLength(){showFull=!showFull;lengthButton.setText(showFull?"كامل":"جزئي");if(adapter!=null)adapter.notifyDataSetChanged();}
 void setFiles(boolean value){for(FileNode node:nodes)node.expanded=value;if(adapter!=null)adapter.notifyDataSetChanged();}
 void setResults(boolean value){for(FileNode node:nodes)for(Result result:node.results)result.expanded=value;if(adapter!=null)adapter.notifyDataSetChanged();}
 void syncActive(){
  if(activeResult<0||activeResult>=results.size())return;Result result=results.get(activeResult);status.setText("مزامنة: "+result.fileName+" — فقرة "+result.paragraph);
 }
 void startSearch(){
  if(searching||files==null||files.isEmpty())return;searching=true;status.setText("جار البحث في "+files.size()+" ملف…");results.clear();nodes.clear();adapter.notifyDataSetChanged();
  worker.execute(()->{
   String normalized=query.toLowerCase().replaceAll("\\s+"," ").trim();String[] tokens=normalized.split(" ");
   for(LibraryActivity.WordFile file:files){
    if(Thread.currentThread().isInterrupted())return;
    try(InputStream input=getContentResolver().openInputStream(file.uri);XWPFDocument document=new XWPFDocument(input);XWPFWordExtractor extractor=new XWPFWordExtractor(document)){
     String[] paragraphs=extractor.getParagraphText();
     for(int i=0;i<paragraphs.length;i++){
      String text=paragraphs[i]==null?"":paragraphs[i];String normalizedText=text.toLowerCase().replaceAll("\\s+"," ").trim();boolean match=true;
      for(String token:tokens)if(!normalizedText.contains(token)){match=false;break;}
      if(match){Result result=new Result(file.name,i+1,text);runOnUiThread(()->{results.add(result);boolean found=false;for(FileNode node:nodes)if(node.name.equals(file.name)){node.results.add(result);found=true;break;}if(!found){FileNode node=new FileNode(file.name);node.results.add(result);nodes.add(node);}adapter.notifyDataSetChanged();status.setText("نتائج: "+results.size());});}
     }
    }catch(Exception ignored){}
   }
   runOnUiThread(()->{searching=false;if(results.isEmpty())status.setText("لا نتائج");});
  });
 }
 void copyAll(){
  if(results.isEmpty())return;StringBuilder text=new StringBuilder();for(int i=0;i<results.size();i++){Result result=results.get(i);text.append("نتيجة ").append(i+1).append(" — ").append(result.fileName).append(" — فقرة ").append(result.paragraph).append("\n").append(result.text).append("\n\n");}
  copy(text.toString(),"تم نسخ جميع النتائج");
 }
 void copy(String text,String message){
  try{ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard==null)throw new IllegalStateException();clipboard.setPrimaryClip(ClipData.newPlainText("نتائج البحث",text));Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}catch(Exception error){Toast.makeText(this,"تعذر النسخ",Toast.LENGTH_LONG).show();}
 }
 final class TreeAdapter extends RecyclerView.Adapter<RowHolder>{
  @Override public RowHolder onCreateViewHolder(ViewGroup parent,int type){
   LinearLayout row=new LinearLayout(SearchResultsActivity.this);row.setOrientation(1);row.setPadding(dp(10),dp(8),dp(10),dp(8));
   android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFFFFFFFF);bg.setCornerRadius(dp(8));bg.setStroke(dp(1),0xFFBDD1DF);row.setBackground(bg);
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(6);row.setLayoutParams(lp);
   LinearLayout top=new LinearLayout(SearchResultsActivity.this);top.setOrientation(0);top.setGravity(Gravity.CENTER_VERTICAL);
   TextView label=new TextView(SearchResultsActivity.this);label.setTextSize(13);label.setTextColor(0xFF315D7B);
   Button copy=new Button(SearchResultsActivity.this);copy.setText("نسخ");copy.setTextSize(11);copy.setMinWidth(0);copy.setMinimumWidth(0);copy.setPadding(dp(8),0,dp(8),0);
   top.addView(label,new LinearLayout.LayoutParams(0,-2,1));top.addView(copy,new LinearLayout.LayoutParams(dp(64),dp(32)));row.addView(top);
   TextView text=new TextView(SearchResultsActivity.this);text.setTextSize(16);text.setTextDirection(View.TEXT_DIRECTION_RTL);row.addView(text,new LinearLayout.LayoutParams(-1,-2));
   return new RowHolder(row,label,text,copy);
  }
  @Override public void onBindViewHolder(RowHolder holder,int position){
   int index=0;FileNode currentNode=null;Result currentResult=null;
   for(FileNode node:nodes){
    if(index==position){currentNode=node;currentResult=null;break;}
    index++;
    if(node.expanded)for(Result result:node.results){if(index==position){currentNode=node;currentResult=result;break;}index++;}
   }
   if(currentNode==null)return;
   if(currentResult==null){holder.label.setText("📁 "+currentNode.name+" — "+currentNode.results.size()+" نتيجة");holder.text.setText("");holder.copy.setVisibility(View.GONE);holder.itemView.setOnClickListener(v->{currentNode.expanded=!currentNode.expanded;adapter.notifyDataSetChanged();});}
   else{holder.label.setText("📄 "+currentResult.fileName+" — فقرة "+currentResult.paragraph);holder.text.setTextSize(16);if(showFull){holder.text.setMaxLines(Integer.MAX_VALUE);holder.text.setEllipsize(null);holder.text.setText(currentResult.text);}else{holder.text.setMaxLines(8);holder.text.setEllipsize(TextUtils.TruncateAt.END);holder.text.setText(currentResult.text);}holder.copy.setVisibility(View.VISIBLE);holder.copy.setOnClickListener(v->copy(currentResult.text,"تم نسخ النتيجة"));holder.itemView.setOnClickListener(v->{activeResult=results.indexOf(currentResult);syncActive();adapter.notifyDataSetChanged();});}
  }
  @Override public int getItemCount(){int count=0;for(FileNode node:nodes){count++;if(node.expanded)count+=node.results.size();}return count;}
 }
 static final class RowHolder extends RecyclerView.ViewHolder{final TextView label,text;final Button copy;RowHolder(View row,TextView label,TextView text,Button copy){super(row);this.label=label;this.text=text;this.copy=copy;}}
}
