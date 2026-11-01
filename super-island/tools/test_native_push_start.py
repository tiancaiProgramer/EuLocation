"""Test the production foreground-start handler with controlled Android API doubles."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT.parent / 'Loading/shared-tools'
JDK = TOOLS / 'android-build/jdk/jdk-17.0.20.1+1/bin'
API = TOOLS / 'tmp-libxposed/classes.jar'
ANDROID = TOOLS / 'android-build/sdk/platforms/android-35/android.jar'
OUT = ROOT / 'app/build/native-push-start-tests'
sources = {
 'android/app/Instrumentation.java': '''package android.app;
public class Instrumentation { public void callActivityOnResume(Activity a){} }''',
 'android/app/Activity.java': '''package android.app;
import android.content.*; import android.content.pm.*;
public class Activity {
 public String pkg="me.ele"; public PackageManager pm=new PackageManager(); public int starts;
 public boolean blocked, nullResult;
 public String getPackageName(){return pkg;} public PackageManager getPackageManager(){return pm;}
 public ComponentName startService(Intent intent){ starts++;if(blocked)throw new IllegalStateException("background");
 return nullResult?null:intent.component; }
}''',
 'android/content/ComponentName.java': '''package android.content;
public class ComponentName { final String pkg,name;
 public ComponentName(String p,String n){pkg=p;name=n;}
 public boolean equals(Object o){return o instanceof ComponentName c && pkg.equals(c.pkg)&&name.equals(c.name);}
 public int hashCode(){return pkg.hashCode()+name.hashCode();}
}''',
 'android/content/Intent.java': '''package android.content;
public class Intent { public ComponentName component; public Intent setComponent(ComponentName c){component=c;return this;} }''',
 'android/content/pm/ServiceInfo.java': '''package android.content.pm;
public class ServiceInfo { public boolean enabled=true; public String packageName="me.ele",processName="me.ele:pushservice"; }''',
 'android/content/pm/PackageManager.java': '''package android.content.pm;
import android.content.ComponentName;
public class PackageManager {
 public static final int COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1;
 public int state; public ServiceInfo info=new ServiceInfo();
 public int getComponentEnabledSetting(ComponentName c){return state;}
 public ServiceInfo getServiceInfo(ComponentName c,int flags){return info;}
}''',
 'android/os/SystemClock.java': '''package android.os;
public class SystemClock { public static long now=100000; public static long elapsedRealtime(){return now;} }''',
 'com/ux5/hyperos3/elemeisland/Main.java': '''package com.ux5.hyperos3.elemeisland;
import java.lang.reflect.Method; import io.github.libxposed.api.XposedInterface;
public class Main {
 interface Chain { Object proceed()throws Throwable; Object getArg(int i); }
 interface Interceptor { Object run(Chain chain)throws Throwable; }
 Interceptor handler; int hooks;
 public Builder hook(Method m){hooks++;return new Builder();}
 public void log(int priority,String tag,String text){}
 class Builder { Builder setExceptionMode(XposedInterface.ExceptionMode mode){return this;}
 void intercept(Interceptor h){handler=h;} }
}''',
 'com/ux5/hyperos3/elemeisland/NativePushStartTest.java': '''package com.ux5.hyperos3.elemeisland;
import android.app.Activity; import android.os.SystemClock;
public class NativePushStartTest {
 static int checks;
 static void check(boolean ok){if(!ok)throw new AssertionError("case "+checks);checks++;}
 static Main install(){Main m=new Main();ElemePushService s=new ElemePushService();s.install(m);s.install(m);check(m.hooks==1);return m;}
 static void resume(Main m,Activity activity)throws Throwable{
  Object expected=new Object();int[] calls={0};
  Object actual=m.handler.run(new Main.Chain(){public Object proceed(){calls[0]++;return expected;}
   public Object getArg(int i){return activity;}});
  check(actual==expected && calls[0]==1);
 }
 public static void main(String[] args)throws Throwable{
  Main m=install();Activity a=new Activity();resume(m,a);check(a.starts==1);
  SystemClock.now+=60000;resume(m,a);check(a.starts==1);
  m=install();a=new Activity();a.pkg="other.app";resume(m,a);check(a.starts==0);
  m=install();a=new Activity();a.pm.state=2;resume(m,a);check(a.starts==0);
  m=install();a=new Activity();a.pm.info.enabled=false;resume(m,a);check(a.starts==0);
  m=install();a=new Activity();a.pm.info.processName="com.xiaomi.xmsf";resume(m,a);check(a.starts==0);
  m=install();a=new Activity();a.pm.info.packageName="other.app";resume(m,a);check(a.starts==0);
  m=install();a=new Activity();a.blocked=true;resume(m,a);check(a.starts==1);
  a.blocked=false;resume(m,a);check(a.starts==1);SystemClock.now+=30000;resume(m,a);check(a.starts==2);
  m=install();a=new Activity();a.nullResult=true;resume(m,a);check(a.starts==1);
  a.nullResult=false;SystemClock.now+=30000;resume(m,a);check(a.starts==2);
  m=install();a=new Activity();final Activity target=a;RuntimeException failure=new RuntimeException("native resume failed");
  try{m.handler.run(new Main.Chain(){public Object proceed(){throw failure;}public Object getArg(int i){return target;}});
   throw new AssertionError("exception swallowed");}catch(RuntimeException actual){check(actual==failure);}
  check(a.starts==0);
  System.out.println("Native foreground push-start checks passed: "+checks);
 }
}'''
}
files=[]
for name,text in sources.items():
    path=OUT/'src'/name;path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(text,encoding='utf-8');files.append(path)
classes=OUT/'classes';classes.mkdir(parents=True,exist_ok=True)
cp=f'{API};{ANDROID}'
subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(classes),
                *map(str,files),str(ROOT/'app/src/main/java/com/ux5/hyperos3/elemeisland/ElemePushService.java')],check=True)
subprocess.run([str(JDK/'java.exe'),'-cp',str(classes)+';'+cp,
                'com.ux5.hyperos3.elemeisland.NativePushStartTest'],check=True)
