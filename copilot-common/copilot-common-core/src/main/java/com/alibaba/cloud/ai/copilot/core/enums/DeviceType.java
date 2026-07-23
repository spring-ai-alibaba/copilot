package com.alibaba.cloud.ai.copilot.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备类型（精简版：仅保留 PC）
 *
 * @author yzm
 */
@Getter
@AllArgsConstructor
public enum DeviceType {

    /** pc端 */
    PC("pc");

    private final String device;
}
