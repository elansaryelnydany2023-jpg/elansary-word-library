package com.elansary.wordlibrary;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
public class DiscoveryActivity extends FolderLibraryActivity {
 @Override protected void onCreate(Bundle state){
  super.onCreate(state);
  status.setMaxLines(3);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
  status.setText("فحص Word المحسن 2 — اختر مجلدًا أو ملفات مباشرة. اضغط نتيجة الفحص لعرض التقرير.");
  fileList.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener(){
   @Override public void onChildViewAttachedToWindow(View v){readable(v);}
   @Override public void onChildViewDetachedFromWindow(View v){}
  });
 }
 void readable(View v){if(v instanceof TextView)((TextView)v).setTextColor(0xff182c40);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)readable(g.getChildAt(i));}}
 String column(Cursor c,String key){int i=c.getColumnIndex(key);return i<0||c.isNull(i)?null:c.getString(i);}
 void note(ArrayList<String> errors,String text){if(errors.size()<50)errors.add(text);}
 @Override void walk(Uri tree,String parent,String path,ArrayList<WordFile> out,ArrayList<String> errors,Set<String> visited,Map<String,String> paths,int[] counts){
  if(!visited.add(parent)||Thread.currentThread().isInterrupted())return;
  counts[0]++;
  Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);
  Cursor cursor=null;
  try{
   try{cursor=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null);}catch(Exception first){cursor=getContentResolver().query(children,null,null,null,null);}
   if(cursor==null)throw new java.io.IOException("مزود الملفات لم يرجع قائمة");
   try(Cursor c=cursor){while(c.moveToNext()){
    try{
     String id=column(c,DocumentsContract.Document.COLUMN_DOCUMENT_ID);
     String name=column(c,DocumentsContract.Document.COLUMN_DISPLAY_NAME);
     String mime=column(c,DocumentsContract.Document.COLUMN_MIME_TYPE);
     if(id==null){note(errors,"عنصر بلا معرف في "+path);continue;}
     Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,id);
     if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){walk(tree,id,path+"/"+name,out,errors,visited,paths,counts);continue;}
     String resolved=null;
     if(!isWord(name,mime))try{resolved=getContentResolver().getType(uri);}catch(Exception e){note(errors,"تعذر قراءة النوع: "+name+": "+e.getMessage());}
     if(DocumentsContract.Document.MIME_TYPE_DIR.equals(resolved)){walk(tree,id,path+"/"+name,out,errors,visited,paths,counts);continue;}
     counts[1]++;
     boolean word=isWord(name,mime)||isWord(name,resolved)||isWord(id,null);
     if(name==null||name.trim().isEmpty())try{name=displayName(uri);}catch(Exception e){name=id;}
     word=word||isWord(name,null);
     if(word){out.add(new WordFile(uri,name));paths.put(uri.toString(),path);}
     else{counts[2]++;note(errors,"مستبعد: "+name+" | النوع: "+mime+" | البديل: "+resolved);}
    }catch(Exception e){note(errors,path+": "+e);}
   }}
  }catch(Exception e){note(errors,path+": "+e);}
 }
}
