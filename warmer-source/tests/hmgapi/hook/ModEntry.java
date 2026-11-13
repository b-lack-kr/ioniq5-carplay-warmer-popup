package hmgapi.hook;
public abstract class ModEntry {public void zygoteInit(ModContext m)throws Throwable{} public void appLoaded(ModContext m,LoadedApp a)throws Throwable{} public void onSettingsChanged(ModContext m)throws Throwable{} public void onEnabled(ModContext m,boolean enabled)throws Throwable{}}
