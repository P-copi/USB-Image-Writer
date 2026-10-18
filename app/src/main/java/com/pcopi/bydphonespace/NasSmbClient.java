package com.pcopi.bydphonespace;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;

/** Based on the proven jcifs-ng SMB2/3 approach from Secure-NAS-Backup. */
final class NasSmbClient {
    static final class Entry{
        final String name,url;
        final boolean directory;
        final long size,modified;
        Entry(String name,String url,boolean directory,long size,long modified){
            this.name=name;this.url=url;this.directory=directory;this.size=size;this.modified=modified;
        }
    }
    static final class Link{
        final String host,route;
        final CIFSContext context;
        Link(String h,String r,CIFSContext c){host=h;route=r;context=c;}
        String root(){return "smb://"+host+"/";}
    }
    interface Visit { void onFile(Entry item) throws Exception; }

    static Link connect(NasSettings config)throws Exception{
        if(config.user.isEmpty())throw new IllegalArgumentException("กรุณากรอกบัญชี NAS");
        if(config.pass.isEmpty())throw new IllegalArgumentException("กรุณากรอกรหัสผ่าน NAS");
        CIFSContext ctx=authentication(config.user,config.pass);
        ArrayList<String[]> attempts=new ArrayList<>();
        if(!NasSettings.TAIL.equals(config.mode)&&!config.lan.isEmpty())attempts.add(new String[]{host(config.lan),"LAN"});
        if(!NasSettings.LAN.equals(config.mode)&&!config.tail.isEmpty()){
            String remote=host(config.tail);
            if(attempts.isEmpty()||!remote.equals(attempts.get(0)[0]))attempts.add(new String[]{remote,"Tailscale"});
        }
        if(attempts.isEmpty())throw new IllegalArgumentException("กรุณากรอกที่อยู่ LAN หรือ Tailscale");
        StringBuilder errors=new StringBuilder();
        for(String[] a:attempts){
            String url="smb://"+a[0]+"/";
            try(SmbFile root=new SmbFile(url,ctx)){
                SmbFile[] listed=root.listFiles();
                if(listed!=null)for(SmbFile file:listed){try{file.close();}catch(Exception ignored){}}
                return new Link(a[0],a[1],ctx);
            }catch(Exception e){
                if(errors.length()>0)errors.append(" / ");
                errors.append(a[1]).append(": ").append(friendly(e));
            }
        }
        throw new Exception("เชื่อมต่อ SMB ไม่สำเร็จ — "+errors);
    }

    static List<Entry> list(Link link,String url)throws Exception{
        checkUrl(link,url);
        ArrayList<Entry> result=new ArrayList<>();
        try(SmbFile dir=new SmbFile(url,link.context)){
            SmbFile[] files=dir.listFiles();
            if(files!=null)for(SmbFile f:files){
                try{
                    String name=f.getName();
                    boolean isDir=f.isDirectory();
                    if(name.endsWith("/"))name=name.substring(0,name.length()-1);
                    if(name.isEmpty()||name.equals(".")||name.equals("..")||name.endsWith("$"))continue;
                    long length=isDir?0:f.length();
                    result.add(new Entry(name,f.getPath(),isDir,length,f.lastModified()));
                }finally{try{f.close();}catch(Exception ignored){}}
            }
        }
        Collections.sort(result,(a,b)->a.directory!=b.directory?(a.directory?-1:1):a.name.compareToIgnoreCase(b.name));
        return result;
    }

    static InputStream open(Link link,Entry entry)throws Exception{
        checkUrl(link,entry.url);
        SmbFile file=new SmbFile(entry.url,link.context);
        try{
            InputStream input=file.getInputStream();
            return new java.io.FilterInputStream(input){
                @Override public void close()throws java.io.IOException{
                    try{super.close();}finally{try{file.close();}catch(Exception ignored){}}
                }
            };
        }catch(Exception e){try{file.close();}catch(Exception ignored){}throw e;}
    }

    /** Bounded recursion so a mistaken whole-share selection cannot traverse unlimited data. */
    static void walkImages(Link link,Entry selected,AtomicBoolean cancelled,Visit visit,int[] count)throws Exception{
        if(cancelled.get())throw new InterruptedException("ยกเลิกโดยผู้ใช้");
        if(count[0]>=3000)throw new IllegalStateException("จำกัดการเลือกรูปสูงสุด 3,000 รูปต่อรอบ");
        if(!selected.directory){
            if(isImage(selected.name)){count[0]++;visit.onFile(selected);}
            return;
        }
        walkDir(link,selected.url,cancelled,visit,count,0);
    }

    private static void walkDir(Link link,String url,AtomicBoolean cancel,Visit visit,int[] count,int depth)throws Exception{
        if(cancel.get())throw new InterruptedException("ยกเลิกโดยผู้ใช้");
        if(depth>30)throw new IllegalStateException("โฟลเดอร์ซ้อนลึกเกิน 30 ชั้น");
        for(Entry entry:list(link,url)){
            if(cancel.get())throw new InterruptedException("ยกเลิกโดยผู้ใช้");
            if(entry.directory)walkDir(link,entry.url,cancel,visit,count,depth+1);
            else if(isImage(entry.name)){
                if(count[0]>=3000)throw new IllegalStateException("จำกัดการเลือกรูปสูงสุด 3,000 รูปต่อรอบ");
                count[0]++;visit.onFile(entry);
            }
        }
    }

    static boolean isImage(String file){
        String n=file.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png")
            ||n.endsWith(".webp")||n.endsWith(".heic")||n.endsWith(".heif")
            ||n.endsWith(".bmp");
    }

    private static CIFSContext authentication(String username,String password)throws Exception{
        Properties p=new Properties();
        p.setProperty("jcifs.smb.client.minVersion","SMB202");
        p.setProperty("jcifs.smb.client.maxVersion","SMB311");
        p.setProperty("jcifs.smb.client.connTimeout","8000");
        p.setProperty("jcifs.smb.client.responseTimeout","12000");
        p.setProperty("jcifs.smb.client.soTimeout","15000");
        return new BaseContext(new PropertyConfiguration(p))
            .withCredentials(new NtlmPasswordAuthenticator("",username,password));
    }

    private static String host(String text){
        String h=text.trim().replaceFirst("(?i)^smb://","");
        if(h.endsWith("/"))h=h.substring(0,h.length()-1);
        if(!h.matches("[a-zA-Z0-9._-]+"))throw new IllegalArgumentException("IP/hostname ต้องไม่มีพอร์ตหรือพาธ (ใช้ SMB 445)");
        return h;
    }

    private static void checkUrl(Link link,String url){
        if(url==null||!url.startsWith(link.root()))
            throw new IllegalArgumentException("ปฏิเสธพาธที่ไม่ใช่ NAS ที่เชื่อมต่อ");
    }

    static String friendly(Throwable err){
        String msg=err.getMessage()==null?err.getClass().getSimpleName():err.getMessage();
        String lo=msg.toLowerCase(Locale.ROOT);
        if(lo.contains("logon")||lo.contains("auth"))return "บัญชีหรือรหัสผ่านถูกปฏิเสธ";
        if(lo.contains("denied"))return "ไม่มีสิทธิ์เข้าถึงโฟลเดอร์";
        if(lo.contains("timed")||lo.contains("unreachable")||lo.contains("refused")||lo.contains("connect"))return "ติดต่อ NAS ไม่ได้/หมดเวลา";
        return msg;
    }
}
