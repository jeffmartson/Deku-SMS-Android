package com.afkanerd.deku.DefaultSMS.Models;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import java.util.List;

public class SIMHandler {

    public static List<SubscriptionInfo> getSimCardInformation(Context context) {
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        int simCount = getActiveSimcardCount(context);

        return subscriptionManager.getActiveSubscriptionInfoList();
    }

    public static int getActiveSimcardCount(Context context) {
        SubscriptionManager subscriptionManager =
                (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        return subscriptionManager.getActiveSubscriptionInfoCount();
    }

    private static String getSimStateString(int simState) {
        switch (simState) {
            case TelephonyManager.SIM_STATE_ABSENT:
                return "Absent";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                return "Network locked";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                return "PIN required";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                return "PUK required";
            case TelephonyManager.SIM_STATE_READY:
                return "Ready";
            case TelephonyManager.SIM_STATE_UNKNOWN:
            default:
                return "Unknown";
        }
    }
    public static int getDefaultSimSubscription(Context context) {
        int defaultSmsSubscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId();
        SubscriptionInfo subscriptionInfo = SubscriptionManager.from(context).getActiveSubscriptionInfo(defaultSmsSubscriptionId);

        return subscriptionInfo.getSubscriptionId();
    }

    public static String getSubscriptionName(Context context, int subscriptionId) {
        List<SubscriptionInfo> subscriptionInfos = getSimCardInformation(context);

        for(SubscriptionInfo subscriptionInfo : subscriptionInfos)
            if(subscriptionInfo.getSubscriptionId() == subscriptionId) {
                if(subscriptionInfo.getCarrierName() != null)
                    return subscriptionInfo.getCarrierName().toString();
            }
        return "";
    }

    public static String getOperatorName(Context context, String serviceCenterAddress) {
        if(serviceCenterAddress == null)
            return null;

        SubscriptionManager subscriptionManager = SubscriptionManager.from(context);

        if (subscriptionManager.getActiveSubscriptionInfoCount() > 0) {
            for (SubscriptionInfo subscriptionInfo : subscriptionManager.getActiveSubscriptionInfoList()) {
                String smscNumber = subscriptionInfo.getSubscriptionId() + "";

                // Compare the serviceCenterAddress with the SMS center number
                if (serviceCenterAddress.equals(smscNumber)) {
                    return subscriptionInfo.getCarrierName().toString();
                }
            }
        }

        return null; // Return null if operator name not found or no active subscriptions
    }
}
