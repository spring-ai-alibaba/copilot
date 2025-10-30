package com.alibaba.cloud.ai.copilot.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备类型
 * 针对一套 用户体系
 *
 * @author yzm
 */
@Getter
@AllArgsConstructor
public enum DeviceType {

    /**
     * pc端
     */
    PC("pc"),

    /**
     * app端
     */
    APP("app"),

    /**
     * 小程序端
     */
    XCX("xcx"),

    /**
     * 数据采集
     */
    COLLECAT("collect"),

    /**
     * 树木医生
     */
    TREES_DOCTOR("trees_doctor");

    private final String device;
}
