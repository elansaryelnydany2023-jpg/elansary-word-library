package com.elansary.wordlibrary;
import android.content.Context;
import android.net.Uri;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import java.io.*;
import java.util.ArrayList;
import java.util.zip.*;
final class DocxReader {
 static ArrayList<String> readParagraphs(Context context,Uri uri) throws Exception {
  InputStream raw=context.getContentResolver().openInputStream(uri);
  if(raw==null) throw new IOException("تعذر فتح المستند");
  try(BufferedInputStream input=new BufferedInputStream(raw)) {
   input.mark(8);byte[] sig=new byte[8];int count=0,n;
   while(count<8 && (n=input.read(sig,count,8-count))!=-1) count+=n;
   input.reset();
   if(count==8 && sig[0]==(byte)0xD0 && sig[1]==(byte)0xCF && sig[2]==0x11 && sig[3]==(byte)0xE0 && sig[4]==(byte)0xA1 && sig[5]==(byte)0xB1 && sig[6]==0x1A && sig[7]==(byte)0xE1) {
    ArrayList<String> out=new ArrayList<>();
    try(HWPFDocument doc=new HWPFDocument(input)) {
     Range range=doc.getRange();
     for(int i=0;i<range.numParagraphs();i++) {String text=range.getParagraph(i).text().replace('\u0007',' ').replace('\r',' ').trim();if(!text.isEmpty())out.add(text);}
    }
    return out;
   }
   if(count<2 || sig[0]!='P' || sig[1]!='K') throw new IOException("تنسيق Word غير مدعوم أو ملف تالف");
   ArrayList<String> out=new ArrayList<>();
   try(ZipInputStream zip=new ZipInputStream(input)) {
    ZipEntry entry;boolean found=false;
    while((entry=zip.getNextEntry())!=null) if("word/document.xml".equals(entry.getName())){found=true;break;}
    if(!found)throw new IOException("لم يوجد word/document.xml داخل الملف");
    XmlPullParser parser=Xml.newPullParser();parser.setInput(zip,null);
    StringBuilder current=new StringBuilder();int event=parser.getEventType();
    while(event!=XmlPullParser.END_DOCUMENT) {
     if(event==XmlPullParser.START_TAG && "p".equals(parser.getName()))current.setLength(0);
     else if(event==XmlPullParser.START_TAG && "t".equals(parser.getName()))current.append(parser.nextText());
     else if(event==XmlPullParser.START_TAG && "tab".equals(parser.getName()))current.append('\t');
     else if(event==XmlPullParser.START_TAG && ("br".equals(parser.getName())||"cr".equals(parser.getName())))current.append('\n');
     else if(event==XmlPullParser.END_TAG && "p".equals(parser.getName())){String text=current.toString().trim();if(!text.isEmpty())out.add(text);}
     event=parser.next();
    }
   }
   return out;
  }
 }
}
