package kr.ioniq5.warmerinstall;
import java.io.*;import java.util.*;import hmgapi.engine.Json;
public final class PrepareMapTest {
 static void ok(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] a)throws Exception{
  File base=new File(a[0]),map=new File(base,"map"),root=new File(base,"root"),check=new File(base,"check.json"),points=new File(a[1]),out=new File(base,"out");
  for(String[] entry:PrepareMap.REQUIRED){File f=new File(root,entry[0].substring(1));f.getParentFile().mkdirs();new FileOutputStream(f).close();entry[1]=PrepareMap.md5(f);}
  out.mkdir();PrepareMap.prepare(map,check,points,root,out);
  Map<String,Object> car=Json.object(PrepareMap.read(new File(out,"car.json")));
  ok(PrepareMap.list(car.get("apps")).contains("climate"));ok(Json.object(PrepareMap.read(new File(out,"climate.json"))).size()==6);
  ok(!new File(map,"climate.json").exists());
  File again=new File(base,"collision");again.mkdir();
  File foreign=new File(map,"climate.json");new FileOutputStream(foreign).close();boolean failed=false;
  try{PrepareMap.prepare(map,check,points,root,again);}catch(Throwable e){failed=true;}
  ok(failed&&again.list().length==0);System.out.println("PrepareMap 406→412 staging and collision tests passed; no active writes");
 }
}
