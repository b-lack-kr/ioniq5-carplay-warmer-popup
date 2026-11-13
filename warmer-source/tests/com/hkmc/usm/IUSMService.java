package com.hkmc.usm;
import android.os.IBinder;
import android.os.IInterface;
public interface IUSMService extends IInterface {
 int getFrontSeatHeat_DrvOption();int getFrontSeatHeat_AstOption();int getFrontSeatHeat_DrvState();int getFrontSeatHeat_AstState();
 int getSWRC_SteeringwheelHeatingOpt();int getSWRC_SteeringwheelHeatingSWInf();int getSWRC_SteeringwheelHeatIndStep2();
 void setFrontSeatHeatDrvStep(int value);void setFrontSeatHeatAstStep(int value);void setSWRC_SteeringwheelWarmerSet(int value);
 int getRearSeatHeat_LeftOption();int getRearSeatHeat_RightOption();int getRearSeatHeat_LeftState();int getRearSeatHeat_RightState();
 void setRearSeatHeatLeftStep(int v);void setRearSeatHeatRightStep(int v);
 final class Stub {public static IUSMService remote;public static IUSMService asInterface(IBinder b){return remote;}}
}
