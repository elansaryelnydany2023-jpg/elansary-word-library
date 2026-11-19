package com.elansary.wordlibrary;

import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class FolderLibraryActivity extends LibraryActivity {
 @Override void scanFolder(Uri tree) {
  status.setText("جار فحص المجلد ومجلداته الفرعية…");
  worker.execute(() -> {
   ArrayList<WordFile> found = new ArrayList<>();
   ArrayList<String> errors = new ArrayList<>();
   int[] oldDocs = {0};
   try { walk(tree, DocumentsContract.getTreeDocumentId(tree), found, errors, new HashSet<>(), oldDocs); }
   catch (Exception e) { errors.add(e.getClass().getSimpleName()+": "+e.getMessage()); }
   runOnUiThread(() -> {
    if (isFinishing() || isDestroyed()) return;
    HashSet<String> known = new HashSet<>();
    for (WordFile f : files) known.add(f.uri.toString());
    int added = 0;
    for (WordFile f : found) if (known.add(f.uri.toString())) { files.add(f); added++; }
    fileAdapter.notifyDataSetChanged();
    String message = "ملفات DOCX بالمجلد: "+found.size()+" — المضافة: "+added+" — الإجمالي: "+files.size();
    if (oldDocs[0]>0) message += "\nملفات DOC قديمة غير مدعومة: "+oldDocs[0];
    if (!errors.isEmpty()) message += "\nتعذر قراءة بعض المجلدات ("+errors.size()+"): "+errors.get(0);
    status.setText(message);
   });
  });
 }
 private void walk(Uri tree, String parent, ArrayList<WordFile> out, ArrayList<String> errors, HashSet<String> visited, int[] oldDocs) {
  if (!visited.add(parent) || Thread.currentThread().isInterrupted()) return;
  String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
  try (Cursor c = getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent),columns,null,null,null)) {
   if (c==null) throw new java.io.IOException("لم يرجع مزود الملفات قائمة للمجلد");
   while (c.moveToNext()) {
    String id=c.getString(0), name=c.getString(1), mime=c.getString(2);
    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) walk(tree,id,out,errors,visited,oldDocs);
    else {
     String lower=name==null?"":name.toLowerCase(Locale.ROOT);
     if (lower.endsWith(".docx") || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)) out.add(new WordFile(DocumentsContract.buildDocumentUriUsingTree(tree,id),name==null?"ملف DOCX":name));
     else if (lower.endsWith(".doc")) oldDocs[0]++;
    }
   }
  } catch (Exception e) { errors.add(e.getClass().getSimpleName()+": "+e.getMessage()); }
 }
}
