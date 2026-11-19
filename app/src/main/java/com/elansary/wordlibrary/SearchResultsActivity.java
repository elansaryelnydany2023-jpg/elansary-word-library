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
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

public class SearchResultsActivity extends AppCompatActivity {
 TextView status;RecyclerView treeList;TreeAdapter adapter;Button collapseFilesButton;Button expandFilesButton;Button collapseResultsButton;Button expandResultsButton;Button syncButton;
 ArrayList<LibraryActivity.WordFile> files;String query;ExecutorService worker=Executors.newSingleThreadExecutor();volatile boolean searching;

 static final class Result{final String folder;final String fileName;final int paragraph;final String text;Result(String folder,String fileName,int paragraph,String text){this.folder=folder;this.fileName=fileName;this.paragraph=paragraph;this.text=text;}}
 ArrayList<Result> results=new ArrayList<>();
 LinkedHashMap<String,LinkedHashMap<String,ArrayList<Result>>> tree=new LinkedHashMap<>();
 HashSet<String> expandedFolders=new HashSet<>();HashSet<String> expandedFiles=new HashSet<>();
 String activeFolder;String activeFile;int activeParagraph=-1;

 @Override protected void onCreate(Bundle state){
  super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(12),dp(12),dp(12));setContentView(root);
  LinearLayout controls=new LinearLayout(this);controls.setOrientation(0);controls.setPadding(0,0,0,dp(8));
  collapseFilesButton=new Button(this);collapseFilesButton.setText("طي الملفات");collapseFilesButton.setTextSize(11);collapseFilesButton.setMinWidth(0);collapseFilesButton.setMinimumWidth(0);collapseFilesButton.setPadding(dp(6),0,dp(6),0);collapseFilesButton.setOnClickListener(v->{expandedFiles.clear();adapter.notifyDataSetChanged();});
  expandFilesButton=new Button(this);expandFilesButton.setText("فرد الملفات");expandFilesButton.setTextSize(11);expandFilesButton.setMinWidth(0);expandFilesButton.setMinimumWidth(0);expandFilesButton.setPadding(dp(6),0,dp(6),0);expandFilesButton.setOnClickListener(v->{for(String folder:tree.keySet())for(String file:tree.get(folder).keySet())expandedFiles.add(folder+"|"+file);adapter.notifyDataSetChanged();});
  collapseResultsButton=new Button(this);collapseResultsButton.setText("طي النتائج");collapseResultsButton.setTextSize(11);collapseResultsButton.setMinWidth(0);collapseResultsButton.setMinimumWidth(0);collapseResultsButton.setPadding(dp(6),0,dp(6),0);collapseResultsButton.setOnClickListener(v->{expandedFolders.clear();expandedFiles.clear();adapter.notifyDataSetChanged();});
  expandResultsButton=new Button(this);expandResultsButton.setText("فرد النتائج");expandResultsButton.setTextSize(11);expandResultsButton.setMinWidth(0);expandResultsButton.setMinimumWidth(0);expandResultsButton.setPadding(dp(6),0,dp(6),0);expandResultsButton.setOnClickListener(v->{for(String folder:tree.keySet()){expandedFolders.add(folder);for(String file:tree.get(folder).keySet())expandedFiles.add(folder+"|"+file);}adapter.notifyDataSetChanged();});
  syncButton=new Button(this);syncButton.setText("مزامنة");syncButton.setTextSize(11);syncButton.setMinWidth(0);syncButton.setMinimumWidth(0);syncButton.setPadding(dp(6),0,dp(6),0);syncButton.setOnClickListener(v->syncActive());
  controls.addView(collapseFilesButton,new LinearLayout.LayoutParams(0,-2,1));controls.addView(expandFilesButton,new LinearLayout.LayoutParams(0,-2,1));controls.addView(collapseResultsButton,new LinearLayout.LayoutParams(0,-2,1));controls.addView(expandResultsButton,new LinearLayout.LayoutParams(0,-2,1));controls.addView(syncButton,new LinearLayout.LayoutParams(0,-2,1));root.addView(controls);
  status=new TextView(this);root.addView(status);
  treeList=new RecyclerView(this);treeList.setLayoutManager(new LinearLayoutManager(this));treeList.setItemAnimator(null);root.addView(treeList,new LinearLayout.LayoutParams(-1,0,1));
  files=getIntent().getParcelableArrayListExtra("files");query=getIntent().getStringExtra("query");adapter=new TreeAdapter();treeList.setAdapter(adapter);startSearch();
 }
 int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 void startSearch(){
  if(searching||files==null||files.isEmpty())return;searching=true;status.setText("جار البحث في "+files.size()+" ملف…");results.clear();tree.clear();expandedFolders.clear();expandedFiles.clear();adapter.notifyDataSetChanged();
  worker.execute(()->{
   String normalized=query.toLowerCase().replaceAll("\\s+"," ").trim();String[] tokens=normalized.split(" ");
   for(LibraryActivity.WordFile file:files){
    if(Thread.currentThread().isInterrupted())return;
    try(InputStream input=getContentResolver().openInputStream(file.uri);XWPFDocument document=new XWPFDocument(input);XWPFWordExtractor extractor=new XWPFWordExtractor(document)){
     String[] paragraphs=extractor.getParagraphText();
     for(int i=0;i<paragraphs.length;i++){
      String text=paragraphs[i]==null?"":paragraphs[i];String normalizedText=text.toLowerCase().replaceAll("\\s+"," ").trim();boolean match=true;
      for(String token:tokens)if(!normalizedText.contains(token)){match=false;break;}
      if(match){String folder=file.name.contains("/")?file.name.substring(0,file.name.lastIndexOf('/')):"الهاتف";Result result=new Result(folder,file.name,i+1,text);runOnUiThread(()->{results.add(result);tree.computeIfAbsent(result.folder,k->new LinkedHashMap<>()).computeIfAbsent(result.fileName,k->new ArrayList<>()).add(result);adapter.notifyDataSetChanged();status.setText("نتائج: "+results.size());});}
     }
    }catch(Exception ignored){}
   }
   runOnUiThread(()->{searching=false;if(results.isEmpty())status.setText("لا نتائج");});
  });
 }
 void openResult(Result result){
  activeFolder=result.folder;activeFile=result.fileName;activeParagraph=result.paragraph;expandedFolders.add(result.folder);expandedFiles.add(result.folder+"|"+result.fileName);adapter.notifyDataSetChanged();status.setText("تم فتح نتيجة في "+result.fileName+" — فقرة "+result.paragraph);
 }
 void syncActive(){
  if(activeFolder==null||activeFile==null||activeParagraph<0)return;
  for(Result result:results)if(result.folder.equals(activeFolder)&&result.fileName.equals(activeFile)&&result.paragraph==activeParagraph){openResult(result);return;}
 }
 void copyAll(){
  if(results.isEmpty())return;StringBuilder text=new StringBuilder();for(int i=0;i<results.size();i++){Result result=results.get(i);text.append("نتيجة ").append(i+1).append(" — ").append(result.fileName).append(" — فقرة ").append(result.paragraph).append("\n").append(result.text).append("\n\n");}
  copy(text.toString(),"تم نسخ جميع النتائج");
 }
 void copy(String text,String message){
  try{ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard==null)throw new IllegalStateException();clipboard.setPrimaryClip(ClipData.newPlainText("نتائج البحث",text));Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}catch(Exception error){Toast.makeText(this,"تعذر النسخ",Toast.LENGTH_LONG).show();}
 }
 final class TreeAdapter extends RecyclerView.Adapter<TreeHolder>{
  @Override public TreeHolder onCreateViewHolder(ViewGroup parent,int type){
   LinearLayout row=new LinearLayout(SearchResultsActivity.this);row.setOrientation(1);row.setPadding(dp(8),dp(6),dp(8),dp(6));
   android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFFFFFFFF);bg.setCornerRadius(dp(8));bg.setStroke(dp(1),0xFFBDD1DF);row.setBackground(bg);
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(6);row.setLayoutParams(lp);
   TextView text=new TextView(SearchResultsActivity.this);text.setTextSize(15);text.setTextDirection(View.TEXT_DIRECTION_RTL);row.addView(text,new LinearLayout.LayoutParams(-1,-2));
   return new TreeHolder(row,text);
  }
  @Override public void onBindViewHolder(TreeHolder holder,int position){
   int index=0;boolean done=false;
   for(Map.Entry<String,LinkedHashMap<String,ArrayList<Result>>> folderEntry:tree.entrySet()){
    String folder=folderEntry.getKey();boolean folderOpen=expandedFolders.contains(folder);
    if(index==position){bindFolder(holder,folder,folderEntry.getValue().size(),folderOpen);done=true;break;}index++;
    if(!folderOpen)continue;
    for(Map.Entry<String,ArrayList<Result>> fileEntry:folderEntry.getValue().entrySet()){
     String file=fileEntry.getKey();boolean fileOpen=expandedFiles.contains(folder+"|"+file);
     if(index==position){bindFile(holder,folder,file,fileEntry.getValue().size(),fileOpen);done=true;break;}index++;
     if(!fileOpen)continue;
     for(Result result:fileEntry.getValue()){
      if(index==position){bindResult(holder,result);done=true;break;}index++;
     }
     if(done)break;
    }
    if(done)break;
   }
   if(!done){holder.text.setText("");}
  }
  void bindFolder(TreeHolder holder,String folder,int count,boolean open){
   holder.text.setText((open?"▼ ":"▶ ")+folder+" ("+count+" ملف)");holder.text.setTextSize(16);holder.text.setTextColor(0xFF0F3D57);
   holder.itemView.setOnClickListener(v->{if(expandedFolders.contains(folder))expandedFolders.remove(folder);else expandedFolders.add(folder);adapter.notifyDataSetChanged();});
  }
  void bindFile(TreeHolder holder,String folder,String file,int count,boolean open){
   boolean active=folder.equals(activeFolder)&&file.equals(activeFile);
   holder.text.setText((open?"▼ ":"▶ ")+file+" ("+count+" نتيجة)");holder.text.setTextSize(15);holder.text.setTextColor(active?0xFF7A4F00:0xFF1F3D4D);
   android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(active?0xFFFFF3C4:0xFFFFFFFF);bg.setCornerRadius(dp(8));bg.setStroke(dp(1),active?0xFFE0A800:0xFFBDD1DF);holder.itemView.setBackground(bg);
   holder.itemView.setOnClickListener(v->{String key=folder+"|"+file;if(expandedFiles.contains(key))expandedFiles.remove(key);else expandedFiles.add(key);adapter.notifyDataSetChanged();});
  }
  void bindResult(TreeHolder holder,Result result){
   boolean active=folderActive(result)&&result.paragraph==activeParagraph;
   holder.text.setText("• فقرة "+result.paragraph+": "+result.text);holder.text.setTextSize(14);
   android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(active?0xFFFFDC80:0xFFF7FAFC);bg.setCornerRadius(dp(8));bg.setStroke(dp(1),active?0xFFE0A800:0xFFD7E4EC);holder.itemView.setBackground(bg);
   holder.itemView.setOnClickListener(v->openResult(result));
  }
  boolean folderActive(Result result){return result.folder.equals(activeFolder)&&result.fileName.equals(activeFile);}
  @Override public int getItemCount(){
   int count=0;
   for(Map.Entry<String,LinkedHashMap<String,ArrayList<Result>>> folderEntry:tree.entrySet()){
    count++;if(!expandedFolders.contains(folderEntry.getKey()))continue;
    for(Map.Entry<String,ArrayList<Result>> fileEntry:folderEntry.getValue().entrySet()){count++;if(expandedFiles.contains(folderEntry.getKey()+"|"+fileEntry.getKey()))count+=fileEntry.getValue().size();}
   }
   return count;
  }
 }
 static final class TreeHolder extends RecyclerView.ViewHolder{final TextView text;TreeHolder(View row,TextView text){super(row);this.text=text;}}
}
