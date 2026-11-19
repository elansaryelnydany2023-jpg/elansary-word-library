package com.elansary.wordlibrary;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.concurrent.*;

public class LibraryActivity extends AppCompatActivity {
 EditText searchBox;Button searchButton;TextView status;RecyclerView fileList;FileAdapter fileAdapter;
 ArrayList<WordFile> files=new ArrayList<>();ExecutorService worker=Executors.newSingleThreadExecutor();

 static final class WordFile{final Uri uri;final String name;WordFile(Uri uri,String name){this.uri=uri;this.name=name;}}

 @Override protected void onCreate(Bundle state){
  super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(12),dp(12),dp(12));setContentView(root);
  TextView title=new TextView(this);title.setText("الأنصاري — مكتبة Word");title.setTextSize(22);root.addView(title);
  LinearLayout searchRow=new LinearLayout(this);searchRow.setOrientation(0);searchRow.setPadding(0,dp(8),0,dp(8));
  searchBox=new EditText(this);searchBox.setHint("ابحث في ملفات Word");searchBox.setTextSize(16);searchRow.addView(searchBox,new LinearLayout.LayoutParams(0,-2,1));
  searchButton=new Button(this);searchButton.setText("بحث");searchRow.addView(searchButton,new LinearLayout.LayoutParams(-2,dp(48)));root.addView(searchRow);
  status=new TextView(this);status.setText("اختر مجلدات أو ابحث في كل ملفات Word بالهاتف");root.addView(status);
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);actions.setPadding(0,dp(8),0,dp(8));
  Button pick=new Button(this);pick.setText("اختيار مجلد");pick.setOnClickListener(v->pickFolder());actions.addView(pick,new LinearLayout.LayoutParams(0,-2,1));
  Button all=new Button(this);all.setText("كل ملفات Word");all.setOnClickListener(v->scanAll());actions.addView(all,new LinearLayout.LayoutParams(0,-2,1));root.addView(actions);
  fileList=new RecyclerView(this);fileList.setLayoutManager(new LinearLayoutManager(this));fileAdapter=new FileAdapter();fileList.setAdapter(fileAdapter);root.addView(fileList,new LinearLayout.LayoutParams(-1,0,1));
  searchButton.setOnClickListener(v->startSearch());
 }
 int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 void pickFolder(){
  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,11);
 }
 @Override protected void onActivityResult(int code,int result,Intent data){
  super.onActivityResult(code,result,data);
  if(code==11&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri folder=data.getData();try{getContentResolver().takePersistableUriPermission(folder,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}scanFolder(folder);}
 }
 void scanFolder(Uri folder){status.setText("جار فحص المجلد…");worker.execute(()->{ArrayList<WordFile> found=new ArrayList<>();collect(folder,found);runOnUiThread(()->{files.clear();files.addAll(found);fileAdapter.notifyDataSetChanged();status.setText("تم العثور على "+found.size()+" ملف Word");});});}
 void collect(Uri folder,ArrayList<WordFile> out){
  try{
   Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(folder,DocumentsContract.getDocumentId(folder));
   try(Cursor cursor=getContentResolver().query(children,null,null,null,null)){
    if(cursor==null)return;while(cursor.moveToNext()){
     String id=cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID));String name=cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME));String mime=cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE));
     Uri child=DocumentsContract.buildDocumentUriUsingTree(folder,id);
     if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))collect(child,out);
     else if(name!=null&&(name.toLowerCase().endsWith(".docx")||name.toLowerCase().endsWith(".doc")))out.add(new WordFile(child,name));
    }
   }
  }catch(Exception ignored){}
 }
 void scanAll(){
  status.setText("جار فحص ملفات Word في الهاتف…");worker.execute(()->{
   ArrayList<WordFile> found=new ArrayList<>();String[] projection={MediaStore.Files.FileColumns._ID,MediaStore.Files.FileColumns.DISPLAY_NAME};
   try(Cursor cursor=getContentResolver().query(MediaStore.Files.getContentUri("external"),projection,MediaStore.Files.FileColumns.MIME_TYPE+" LIKE ? OR "+MediaStore.Files.FileColumns.DISPLAY_NAME+" LIKE ?",new String[]{"application/vnd.openxmlformats-officedocument.wordprocessingml.document","%.docx"},null)){
    if(cursor!=null)while(cursor.moveToNext()){long id=cursor.getLong(0);String name=cursor.getString(1);found.add(new WordFile(ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"),id),name==null?"ملف Word":name));}
   }catch(Exception ignored){}
   runOnUiThread(()->{files.clear();files.addAll(found);fileAdapter.notifyDataSetChanged();status.setText("تم العثور على "+found.size()+" ملف Word");});
  });
 }
 void startSearch(){
  String query=searchBox.getText().toString().trim();if(query.isEmpty()){status.setText("اكتب كلمة أو أكثر للبحث");return;}
  if(files.isEmpty()){status.setText("لا توجد ملفات Word؛ اختر مجلدًا أو افحص كل ملفات Word");return;}
  InputMethodManager keyboard=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(searchBox.getWindowToken(),0);
  Intent intent=new Intent(this,SearchResultsActivity.class);intent.putExtra("query",query);intent.putParcelableArrayListExtra("files",new ArrayList<>(files));startActivity(intent);
 }
 final class FileAdapter extends RecyclerView.Adapter<FileHolder>{
  @Override public FileHolder onCreateViewHolder(ViewGroup parent,int type){
   LinearLayout card=new LinearLayout(LibraryActivity.this);card.setOrientation(1);card.setPadding(dp(12),dp(10),dp(12),dp(10));
   android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFFFFFFFF);bg.setCornerRadius(dp(10));bg.setStroke(dp(1),0xFFBDD1DF);card.setBackground(bg);card.setPadding(dp(12),dp(10),dp(12),dp(10));
   RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(8);card.setLayoutParams(lp);
   TextView name=new TextView(LibraryActivity.this);name.setTextSize(16);card.addView(name);return new FileHolder(card,name);
  }
  @Override public void onBindViewHolder(FileHolder holder,int position){holder.name.setText(files.get(position).name);}
  @Override public int getItemCount(){return files.size();}
 }
 static final class FileHolder extends RecyclerView.ViewHolder{final TextView name;FileHolder(View card,TextView name){super(card);this.name=name;}}
}
