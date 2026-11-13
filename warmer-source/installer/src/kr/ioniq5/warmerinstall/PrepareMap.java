package kr.ioniq5.warmerinstall;
import hmgapi.engine.Json;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
/** Offline staging only. Never writes active maps or state. */
public final class PrepareMap {
 static final String PRODUCT="NEEV.KOR.0000.V039.001.260902", FOLDER="hyundai/neev/kor/260902";
 static final String[][] REQUIRED={{"/system/app/HKMC_Climate.odex","261364015ab1723bd2ebec18d21e4062"},{"/system/framework/automotive.odex","c76b8209ef171739b177eb14e8df61dd"},{"/system/framework/com.broadcom.bt.jar","dbf374aa8bde80fad95746a67719ce51"},{"/system/framework/com.telechips.android.tcciap.odex","f612c3c05d459c8da4069a9139c0b323"},{"/system/framework/com.tcc.iap2.odex","e9bb5e16b11a6feefa593d3f2ea7d961"}};
 static void need(boolean x,String e){if(!x)throw new IllegalStateException(e);}
 static String read(File f)throws Exception{need(f.isFile()&&f.getCanonicalFile().equals(f.getAbsoluteFile())&&f.length()<=2000000,"BAD_FILE");ByteArrayOutputStream b=new ByteArrayOutputStream();InputStream i=new FileInputStream(f);try{byte[] x=new byte[8192];int n;while((n=i.read(x))!=-1)b.write(x,0,n);}finally{i.close();}return b.toString("UTF-8");}
 static String md5(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("MD5");InputStream i=new FileInputStream(f);try{byte[] b=new byte[8192];int n;while((n=i.read(b))!=-1)md.update(b,0,n);}finally{i.close();}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
 @SuppressWarnings("unchecked") static Map<String,Object> obj(Object o){need(o instanceof Map,"OBJECT");return (Map<String,Object>)o;}
 @SuppressWarnings("unchecked") static List<Object> list(Object o){need(o instanceof List,"ARRAY");return (List<Object>)o;}
 static void write(File f,String s)throws Exception{need(!f.exists()&&f.getCanonicalFile().equals(f.getAbsoluteFile()),"OUTPUT_EXISTS");FileOutputStream o=new FileOutputStream(f);try{o.write(s.getBytes("UTF-8"));o.getFD().sync();}finally{o.close();}}
 static void prepare(File map,File check,File points,File root,File out)throws Exception{
  need(map.isDirectory()&&map.getCanonicalFile().equals(map.getAbsoluteFile())&&out.isDirectory()&&out.list().length==0,"PATH");
  Map<String,Object> car=Json.object(read(new File(map,"car.json"))),state=Json.object(read(check)),adds=Json.object(read(points));
  need("kor".equals(car.get("region"))&&"260902".equals(car.get("firmware")),"CAR");
  boolean product=false;for(Object p:list(car.get("products")))if(p instanceof String&&PRODUCT.startsWith((String)p))product=true;need(product,"PRODUCT");
  need(Boolean.TRUE.equals(state.get("ok"))&&PRODUCT.equals(state.get("product"))&&FOLDER.equals(state.get("folder")),"STATE");
  Map<String,Object> files=obj(car.get("files"));need(files.equals(obj(state.get("files"))),"STALE_STATE");
  String expected="{\"climate.warmer\":{\"class\":\"com.hkmc.telematics.service.apps.climate.ClimateManager\"},\"climate.warmer.open\":{\"method\":\"mD\",\"params\":[],\"ret\":\"void\",\"static\":false},\"climate.listener\":{\"class\":\"com.hkmc.telematics.service.apps.climate.j\"},\"climate.listener.front\":{\"method\":\"onFrontSeatHeatStatusReceived\",\"params\":[\"int\",\"int\",\"int\",\"int\"],\"ret\":\"void\",\"static\":false},\"climate.listener.rear\":{\"method\":\"onRearSeatHeatStatusReceived\",\"params\":[\"int\",\"int\",\"int\",\"int\"],\"ret\":\"void\",\"static\":false},\"climate.listener.wheel\":{\"method\":\"onSWRCStatusReceived\",\"params\":[\"int\",\"int\",\"int\",\"int\"],\"ret\":\"void\",\"static\":false}}";
  need(adds.equals(Json.object(expected)),"POINTS_UNEXPECTED");
  List<Object> apps=list(car.get("apps"));need(!apps.contains("climate")&&!new File(map,"climate.json").exists(),"CLIMATE_EXISTS");
  Set<String> seen=new HashSet<String>(),allPoints=new HashSet<String>();int count=0;
  for(Object a:apps){need(a instanceof String&&((String)a).matches("[a-z0-9_]+")&&seen.add((String)a),"APPS");Map<String,Object> m=Json.object(read(new File(map,a+".json")));for(String key:m.keySet())need(allPoints.add(key)&&!adds.containsKey(key),"POINT_COLLISION");count+=m.size();}
  need(count==406&&state.get("points") instanceof Number&&((Number)state.get("points")).intValue()==count,"POINT_COUNT");
  List<Object> paths=new ArrayList<Object>();for(String[] e:REQUIRED){File actual=new File(root,e[0].substring(1));need(actual.isFile()&&e[1].equals(md5(actual)),"STOCK_HASH");Object old=files.get(e[0]);need(old==null||old.equals(e[1]),"HASH_CONFLICT");files.put(e[0],e[1]);paths.add(e[0]);}
  Map<String,Object> af=obj(car.get("app_files"));need(!af.containsKey("climate"),"APP_CONFLICT");Map<String,Object> entry=new LinkedHashMap<String,Object>();entry.put("package","com.hkmc.telematics.service.apps.climate");entry.put("files",paths);af.put("climate",entry);apps.add("climate");
  Object missing=car.get("missing");if(missing instanceof Map)for(String key:adds.keySet())obj(missing).remove(key);
  car.put("status","probed");car.put("verified",null);write(new File(out,"car.json"),Json.write(car));write(new File(out,"climate.json"),Json.write(adds));
  System.out.println("STAGED_412_NOT_INSTALLED");
 }
 public static void main(String[] args){try{need(args.length==5,"ARGS");prepare(new File(args[0]),new File(args[1]),new File(args[2]),new File(args[3]),new File(args[4]));}catch(Throwable t){System.out.println("MAP_PREPARE_FAILED "+t.getMessage());System.exit(2);}}
}
