import React, { useEffect, useState } from "react";
import { getTokenUsage } from "../../api/tokens";
import { Spin } from "antd";
import useUserStore, { TierType } from "../../stores/userSlice";
import { useTranslation } from "react-i18next";

export function QuotaSettings() {
  const { t } = useTranslation();
  const { user, fetchUser, isLoading: loading, openLoginModal } = useUserStore();

  useEffect(() => {
    fetchUser();
  }, []);

  if(!user?.id){
    return (
      <div className="flex flex-col items-center justify-center h-[200px] text-gray-400">
        <div className="mb-4 translate">{t("common.please_login")}</div>
        <button
          onClick={() => {
            openLoginModal()
          }}
          className="bg-blue-500 hover:bg-blue-600 text-white rounded-lg px-4 py-1.5 transition-colors text-[14px]"
        >
          <span className="translate">{t("common.login")}</span>
        </button>
      </div>
    );
  }

  const userQuota = user.userQuota || {
    quota: 0,
    resetTime: new Date(),
    tierType: TierType.FREE,
    refillQuota: 0,
    usedQuota: 0,
    quotaTotal: 0,
    tierMessage:{
      startTime: new Date(),
      tier: TierType.FREE,
      resetTime: new Date()
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-[200px]">
        <Spin />
      </div>
    );
  }

  if (!userQuota) return null;

  const usagePercent = (userQuota.usedQuota / userQuota.quotaTotal) * 100;

  return (
    <div>
      <h2 className="text-[16px] font-medium text-white mb-4 translate">
        {t("usage.usage")}
      </h2>

      {/* Progress Card */}
      <div className="bg-blue-500/85 dark:bg-blue-700/20 rounded-lg p-3 mb-4">
        <div className="flex items-center justify-between mb-3">
          <div className="text-[15px] text-white translate">
            {userQuota.quotaTotal} {t("usage.monthly_usage")}
          </div>
        </div>

        {/* Progress bar */}
        <div className="bg-black/20 rounded-full h-1.5 mb-2">
          <div
            className="bg-blue-500 h-full rounded-full"
            style={{ width: `${Math.min(usagePercent, 100)}%` }}
          />
        </div>
        <div className="flex justify-between text-[14px]">
          <span className="text-white translate">
            {userQuota.usedQuota} {t("usage.quota_used")}
          </span>
          {/* <span className="text-white translate">
            {remaining.toLocaleString()} {t("usage.remaining")}
          </span> */}
        </div>
      </div>

      {/* Usage Stats */}
      <div className="space-y-4">
        <div className="flex justify-between items-center text-[14px]">
          <span className="text-gray-400 translate">
            {t("usage.billing_cycle")}
          </span>
          <span className=" dark:text-white translate">
            {userQuota.resetTime?.toLocaleString() || "1970-01-01"}
          </span>
        </div>

        <div className="flex justify-between items-center text-[14px]">
          <span className="text-gray-400 translate">{t("usage.type")}</span>
          <span className="dark:text-white">{userQuota.tierType}</span>
        </div>
      </div>

      {/* Buy More Button */}
      <button
        onClick={() => {
          const url = "https://we0.ai/user";
          if (window.electron?.ipcRenderer) {
            window.electron.ipcRenderer.send("open:external:url", url);
          } else {
            window.open(url, "_blank", "noopener,noreferrer");
          }
        }}
        className="mt-8 w-full bg-blue-500 hover:bg-blue-600 text-white rounded-lg px-4 py-1.5 transition-colors text-[14px]"
      >
        <span className="translate">{t("usage.buy_quote")}</span>
      </button>
    </div>
  );
}
