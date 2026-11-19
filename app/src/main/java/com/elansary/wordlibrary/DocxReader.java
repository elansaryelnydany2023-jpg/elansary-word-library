package com.elansary.wordlibrary;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class DocxReader {
 static ArrayList<String> readParagraphs(Context context,Uri uri) throws Exception {
  ArrayList<String> paragraphs=new ArrayList<>();
  try(InputStream input=context.getContentResolver().openInputStream(uri);ZipInputStream zip=new ZipInputStream(input)){
   ZipEntry entry;boolean found=false;
   while((entry=zip.getNextEntry())!=null){
    if("word/document.xml".equals(entry.getName())){found=true;break;}
   }
   if(!found)return paragraphs;
   XmlPullParser parser=Xml.newPullParser();parser.setInput(zip,"UTF-8");
   StringBuilder current=new StringBuilder();int event=parser.getEventType();
   while(event!=XmlPullParser.END_DOCUMENT){
    if(event==XmlPullParser.START_TAG&&"p".equals(parser.getName())){current.setLength(0);}
    else if(event==XmlPullParser.START_TAG&&"t".equals(parser.getName())){String text=parser.nextText();if(text!=null)current.append(text);}
    else if(event==XmlPullParser.END_TAG&&"p".equals(parser.getName())){String text=current.toString().trim();if(!text.isEmpty())paragraphs.add(text);}
    event=parser.next();
   }
  }
  return paragraphs;
 }
}
