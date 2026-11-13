package android.os; public class Looper {private static final Looper x=new Looper(); public static Looper getMainLooper(){return x;} public static Looper myLooper(){return x;}}
