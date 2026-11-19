package com.elansary.wordlibrary;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.*;
public class FolderLibraryActivity extends LibraryActivity {
 @Override void scanAll(){pickFolder();}
 @Override void scanFolder(Uri tree){
  status.setText("جار فحص DOC وDOCX بالمجلد ومجلداته الفرعية…");
  worker.execute(()->{
   ArrayList<WordFile> found=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();HashMap<String,String> paths=new HashMap<>();
   try{String id=DocumentsContract.getTreeDocumentId(tree);walk(tree,id,"المجلد المختار",found,errors,new HashSet<>(),paths);}catch(Exception e){errors.add(e.toString());}
   runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;
    android.content.SharedPreferences.Editor saved=getSharedPreferences("file_paths",0).edit();
    for(Map.Entry<String,String> entry:paths.entrySet())saved.putString(entry.getKey(),entry.getValue());saved.apply();
    HashSet<String> known=new HashSet<>();for(WordFile f:files)known.add(f.uri.toString());
    int added=0;for(WordFile f:found)if(known.add(f.uri.toString())){files.add(f);added++;}
    fileAdapter.notifyDataSetChanged();String message="ملفات Word: "+found.size()+" — المضافة: "+added+" — الإجمالي: "+files.size();
    if(!errors.isEmpty())message+="\nأخطاء الوصول: "+errors.size()+" — "+errors.get(0);status.setText(message);
   });
  });
 }
 private void walk(Uri tree,String parent,String path,ArrayList<WordFile> out,ArrayList<String> errors,HashSet<String> visited,HashMap<String,String> paths){
  if(!visited.add(parent)||Thread.currentThread().isInterrupted())return;
  String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
  try(Cursor c=getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent),projection,null,null,null)){
   if(c==null)throw new java.io.IOException("تعذر قراءة المجلد");
   String label=path;
   if("المجلد المختار".equals(path))try(Cursor root=getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(tree,parent),new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(root!=null&&root.moveToFirst()&&root.getString(0)!=null)label=root.getString(0);}catch(Exception ignored){}
   while(c.moveToNext()){
    String id=c.getString(0),name=c.getString(1),mime=c.getString(2);
    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))walk(tree,id,label+"/"+(name==null?"مجلد":name),out,errors,visited,paths);
    else{String lower=name==null?"":name.toLowerCase(Locale.ROOT);
     if(lower.endsWith(".doc")||lower.endsWith(".docx")||"application/msword".equals(mime)||"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)){
      Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,id);out.add(new WordFile(uri,name==null?"ملف Word":name));paths.put(uri.toString(),label);
     }
    }
   }
  }catch(Exception e){errors.add(path+": "+e);}
 }
}
