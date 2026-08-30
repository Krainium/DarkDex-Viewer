package com.krainium.dexviewer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.Adler32;

final class DexFile {
    static final class Entry { final int index, offset, byteLength; final String value; Entry(int i,int o,int n,String v){index=i;offset=o;byteLength=n;value=v;} }
    final byte[] data; final String version; final int fileSize, headerSize, endianTag, stringCount, typeCount, protoCount, fieldCount, methodCount, classCount;
    private final ArrayList<Entry> strings = new ArrayList<>();

    DexFile(byte[] bytes) throws IOException {
        if(bytes.length < 112 || bytes[0]!='d'||bytes[1]!='e'||bytes[2]!='x'||bytes[3]!='\n') throw new IOException("Not a standard DEX file");
        data=bytes; version=new String(bytes,4,3,StandardCharsets.US_ASCII);
        fileSize=u32(32); headerSize=u32(36); endianTag=u32(40);
        if(headerSize < 112 || fileSize > bytes.length || fileSize < 112) throw new IOException("Invalid or truncated DEX header");
        stringCount=u32(56); int stringOff=u32(60); typeCount=u32(64); protoCount=u32(72); fieldCount=u32(80); methodCount=u32(88); classCount=u32(96);
        if(stringCount < 0 || stringCount > 4_000_000 || ((long)stringOff + (long)stringCount*4)>bytes.length) throw new IOException("Invalid string table");
        for(int i=0;i<stringCount;i++){ int off=u32(stringOff+i*4); if(off<0||off>=bytes.length) {strings.add(new Entry(i,0,0,"<invalid>"));continue;} int p=skipUleb(off); int start=p; while(p<bytes.length&&bytes[p]!=0)p++; strings.add(new Entry(i,start,p-start,decode(start,p-start))); }
    }
    int u32(int p) throws IOException { if(p<0||p+4>data.length)throw new IOException("Out of range"); return (data[p]&255)|((data[p+1]&255)<<8)|((data[p+2]&255)<<16)|((data[p+3]&255)<<24); }
    private int skipUleb(int p){ for(int i=0;i<5&&p<data.length;i++,p++) if((data[p]&128)==0)return p+1; return p; }
    private String decode(int p,int n){ try{return new String(data,p,n,StandardCharsets.UTF_8);}catch(Exception e){return "<invalid utf8>";} }
    List<Entry> strings(){return Collections.unmodifiableList(strings);}
    String summary(){return "DEX "+version+"  •  "+format(data.length)+"\n"+classCount+" classes  •  "+methodCount+" methods  •  "+fieldCount+" fields\n"+stringCount+" strings  •  "+typeCount+" types  •  "+protoCount+" prototypes";}
    static String format(long n){if(n>=1048576)return String.format(Locale.US,"%.1f MB",n/1048576.0);if(n>=1024)return String.format(Locale.US,"%.1f KB",n/1024.0);return n+" B";}
    void patchBytes(int offset, byte[] patch) throws IOException {if(offset<0||offset+patch.length>data.length)throw new IOException("Patch is outside the file");System.arraycopy(patch,0,data,offset,patch.length);fixIntegrity();}
    void replaceString(Entry e,String value)throws IOException{byte[] b=value.getBytes(StandardCharsets.UTF_8);if(b.length!=e.byteLength)throw new IOException("Replacement must be exactly "+e.byteLength+" UTF-8 bytes (got "+b.length+")");patchBytes(e.offset,b);}
    void fixIntegrity()throws IOException{try{MessageDigest md=MessageDigest.getInstance("SHA-1");md.update(data,32,data.length-32);byte[] sig=md.digest();System.arraycopy(sig,0,data,12,20);Adler32 a=new Adler32();a.update(data,12,data.length-12);int v=(int)a.getValue();for(int i=0;i<4;i++)data[8+i]=(byte)(v>>>(8*i));}catch(Exception e){throw new IOException("Could not update DEX integrity",e);}}
    String hexWindow(int center){int from=Math.max(0,(center-128)&~15),to=Math.min(data.length,from+512);StringBuilder s=new StringBuilder();for(int p=from;p<to;p+=16){s.append(String.format(Locale.US,"%08X  ",p));for(int i=0;i<16;i++)s.append(p+i<to?String.format(Locale.US,"%02X ",data[p+i]&255):"   ");s.append(" ");for(int i=0;i<16&&p+i<to;i++){int c=data[p+i]&255;s.append(c>=32&&c<127?(char)c:'.');}s.append('\n');}return s.toString();}
}
