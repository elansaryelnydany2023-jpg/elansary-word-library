package com.elansary.wordlibrary;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.*;
import android.view.*;
import android.widget.*;
import java.util.*;
public class FolderLibraryActivity extends LibraryActivity {
 static final int PICK_FILES=42;
 static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
 @Override protected void onCreate(Bundle state){super.onCreate(state);renameActions(findViewById(android.R.id.content));status.setText("اختر مجلدًا ثم استخدام هذا المجلد وسماح، أو اختر ملفات Word مباشرة");}
 void renameActions(View view){if(view instanceof Button&&((Button)view).getText().toString().equals("كل ملفات Word"))((Button)view).setText("اختيار ملفات Word");if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)renameActions(group.getChildAt(i));}}
 @Override void scanAll(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{startActivityForResult(i,PICK_FILES);}catch(Exception e){status.setText("تعذر فتح اختيار الملفات: "+e.getMessage());}}
 @Override protected void onActivityResult(int code,int result,Intent data){
  if(code!=PICK_FILES){super.onActivityResult(code,result,data);return;}
  if(result!=RESULT_OK||data==null)return;
  LinkedHashSet<Uri> selected=new LinkedHashSet<>();if(data.getData()!=null)selected.add(data.getData());if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());
  for(Uri uri:selected)try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
  status.setText("جار فحص الملفات المختارة…");worker.execute(()->{ArrayList<WordFile> found=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();for(Uri uri:selected)try{String name=displayName(uri);if(isWord(name,getContentResolver().getType(uri)))found.add(new WordFile(uri,name));else errors.add("ليس ملف Word معروفًا: "+name);}catch(Exception e){errors.add(e.toString());}publish(found,new HashMap<>(),errors,"مختارة: "+selected.size());});
 }
 String displayName(Uri uri){try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&c.getString(0)!=null)return c.getString(0);}return uri.getLastPathSegment()==null?"ملف Word":uri.getLastPathSegment();}
 static String clean(String s){return s==null?"":s.replaceAll("[\\p{Cf}]","").trim().toLowerCase(Locale.ROOT);}
 static boolean isWord(String name,String mime){String n=clean(name),m=clean(mime);int semi=m.indexOf(';');if(semi>=0)m=m.substring(0,semi).trim();return n.endsWith(".docx")||n.endsWith(".doc")||m.equals("application/msword")||m.equals(DOCX)||m.equals("application/vnd.ms-word.document.12");}
 @Override void scanFolder(Uri tree){status.setText("جار فحص المجلد وفروعه…");worker.execute(()->{ArrayList<WordFile> found=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();HashMap<String,String> paths=new HashMap<>();int[] counts=new int[3];try{walk(tree,DocumentsContract.getTreeDocumentId(tree),"المجلد المختار",found,errors,new HashSet<>(),paths,counts);}catch(Exception e){errors.add(e.toString());}publish(found,paths,errors,"مجلدات: "+counts[0]+" | ملفات مفحوصة: "+counts[1]+" | مستبعدة: "+counts[2]);});}
 void publish(ArrayList<WordFile> found,Map<String,String> paths,ArrayList<String> errors,String detail){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;android.content.SharedPreferences.Editor saved=getSharedPreferences("file_paths",0).edit();for(Map.Entry<String,String> e:paths.entrySet())saved.putString(e.getKey(),e.getValue());saved.apply();HashSet<String> known=new HashSet<>();for(WordFile f:files)known.add(f.uri.toString());int added=0;for(WordFile f:found)if(known.add(f.uri.toString())){files.add(f);added++;}fileAdapter.notifyDataSetChanged();String report=detail+"\nملفات Word: "+found.size()+" | المضافة: "+added+" | الإجمالي: "+files.size();if(found.isEmpty())report+="\nلم تتم إضافة ملفات؛ جرّب اختيار ملفات Word مباشرة.";if(!errors.isEmpty())report+="\nملاحظات وأخطاء: "+errors.size()+"\n"+android.text.TextUtils.join("\n",errors.subList(0,Math.min(10,errors.size())));final String full=report;status.setText(full);status.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("تقرير فحص Word").setMessage(full).setPositiveButton("نسخ",(d,n)->{android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(c!=null)c.setPrimaryClip(ClipData.newPlainText("تقرير",full));}).setNegativeButton("إغلاق",null).show());});}
 void walk(Uri tree,String parent,String path,ArrayList<WordFile> out,ArrayList<String> errors,Set<String> visited,Map<String,String> paths,int[] counts){
  if(!visited.add(parent)||Thread.currentThread().isInterrupted())return;counts[0]++;
  String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
  try(Cursor c=getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent),projection,null,null,null)){
   if(c==null)throw new java.io.IOException("لم يُرجع مزود الملفات قائمة المجلد");
   while(c.moveToNext())try{String id=c.getString(c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)),name=c.getString(c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)),mime=c.getString(c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE));if(id==null){errors.add("عنصر بلا معرف داخل "+path);continue;}if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){walk(tree,id,path+"/"+name,out,errors,visited,paths,counts);continue;}counts[1]++;Uri uri=DocumentsContract.buildDocumentUriUsingTree(tree,id);if(name==null||name.trim().isEmpty())name=displayName(uri);boolean word=isWord(name,mime);if(!word)word=isWord(name,getContentResolver().getType(uri));if(word){out.add(new WordFile(uri,name));paths.put(uri.toString(),path);}else{counts[2]++;if(errors.size()<10)errors.add("مستبعد: "+name+" | النوع: "+mime);}}catch(Exception e){errors.add(path+": "+e);}
  }catch(Exception e){errors.add(path+": "+e);}
 }
}
